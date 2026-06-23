package com.seatrush.ticketservice.domain.reservation.service;

import com.seatrush.ticketservice.common.exception.CustomException;
import com.seatrush.ticketservice.common.metrics.BusinessMetrics;
import com.seatrush.ticketservice.common.response.status.ErrorCode;
import com.seatrush.ticketservice.domain.auth.entity.User;
import com.seatrush.ticketservice.domain.auth.repository.UserRepository;
import com.seatrush.ticketservice.domain.reservation.dto.response.ReservationResponseDto;
import com.seatrush.ticketservice.domain.reservation.dto.response.PaymentRequestResponseDto;
import com.seatrush.ticketservice.domain.reservation.entity.Reservation;
import com.seatrush.ticketservice.domain.reservation.entity.ReservationStatus;
import com.seatrush.ticketservice.domain.reservation.event.model.EntrySlotReleaseReason;
import com.seatrush.ticketservice.domain.reservation.event.publisher.EntrySlotReleaseOutboxWriter;
import com.seatrush.ticketservice.domain.reservation.event.publisher.PaymentRequestOutboxWriter;
import com.seatrush.ticketservice.domain.reservation.repository.ReservationRepository;
import com.seatrush.ticketservice.domain.seat.entity.Seat;
import com.seatrush.ticketservice.domain.seat.entity.SeatStatus;
import com.seatrush.ticketservice.domain.seat.repository.SeatHold;
import com.seatrush.ticketservice.domain.seat.repository.SeatRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;

/**
 * 예매 생성, 상세 조회, 사용자 취소 처리, 결제 요청 연동 등 핵심 예매 비즈니스 로직을 수행하는 서비스 클래스입니다.
 *
 * 트랜잭션 정상 반영(Commit) 완료 시점에 연동되어 비동기로 선점 락을 해제하거나(Outbox 패턴 사용),
 * Kafka를 통해 연동 마이크로서비스로 결제 요청 및 진입 슬롯 반환 이벤트를 전송합니다.
 */
@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final SeatRepository seatRepository;
    private final ReservationHoldReleaseService holdReleaseService;
    private final EntrySlotReleaseOutboxWriter entrySlotReleaseOutboxWriter;
    private final PaymentRequestOutboxWriter paymentRequestOutboxWriter;
    private final BusinessMetrics businessMetrics;

    public ReservationService(
            ReservationRepository reservationRepository,
            UserRepository userRepository,
            SeatRepository seatRepository,
            ReservationHoldReleaseService holdReleaseService,
            EntrySlotReleaseOutboxWriter entrySlotReleaseOutboxWriter,
            PaymentRequestOutboxWriter paymentRequestOutboxWriter,
            BusinessMetrics businessMetrics
    ) {
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
        this.seatRepository = seatRepository;
        this.holdReleaseService = holdReleaseService;
        this.entrySlotReleaseOutboxWriter = entrySlotReleaseOutboxWriter;
        this.paymentRequestOutboxWriter = paymentRequestOutboxWriter;
        this.businessMetrics = businessMetrics;
    }

    /**
     * 검증된 임시 선점(Hold) 정보를 바탕으로 RDB에 정식 예매 기록(PENDING_PAYMENT 상태)을 생성합니다.
     *
     * @param hold 검증된 Redis 선점 정보 객체
     * @param userId 예매를 신청한 로그인 사용자 고유 식별 ID
     * @param expiresAt 결제 대기 마료 시각
     * @return 생성된 예매 정보 Dto
     * @throws CustomException 이미 존재하는 예매거나 좌석 상태가 유효하지 않을 때
     */
    @Transactional
    public ReservationResponseDto create(
            SeatHold hold,
            Long userId,
            LocalDateTime expiresAt
    ) {
        return businessMetrics.record("reservation.create", mode(hold), () -> {
            // 1. 선점된 좌석 정보 조회 및 가용 여부 확인
            List<Seat> seats = businessMetrics.record(
                    "reservation.create.seats.load",
                    mode(hold),
                    () -> findReservationSeats(hold)
            );
            User user = businessMetrics.record(
                    "reservation.create.user.load",
                    mode(hold),
                    () -> userRepository.findById(userId)
                            .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND))
            );

            // 2. 예매 엔티티 생성
            Reservation reservation = Reservation.create(
                    user,
                    seats.getFirst().getSection().getSchedule(),
                    hold.holdId(),
                    hold.entryTokenId(),
                    seats,
                    expiresAt
            );

            try {
                // DB에 영속화 및 제약조건 위반 즉시 감지를 위해 Flush 수행
                businessMetrics.record(
                        "reservation.create.persist",
                        mode(hold),
                        () -> reservationRepository.saveAndFlush(reservation)
                );
            } catch (DataIntegrityViolationException exception) {
                throw new CustomException(ErrorCode.RESERVATION_ALREADY_EXISTS);
            }

            return ReservationResponseDto.from(reservation);
        });
    }

    /**
     * 로그인 사용자의 특정 예매 상세 조회를 수행하며,
     * 조회 시점에 결제 유효시각이 초과되었을 경우 즉시 예매를 '만료(EXPIRED)' 처리하고 Outbox에 이벤트를 적재합니다.
     *
     * @param reservationId 예매 ID
     * @param userId 조회 요청자 ID
     * @return 예매 정보 Dto
     * @throws CustomException 예매 정보를 찾지 못하는 경우
     */
    @Transactional
    public ReservationResponseDto get(Long reservationId, Long userId) {
        Reservation reservation = findOwnedReservation(reservationId, userId);
        expireIfNeeded(reservation, LocalDateTime.now());
        return ReservationResponseDto.from(reservation);
    }

    /**
     * 결제 대기 중인 예매를 사용자가 직접 취소(Cancel) 처리합니다.
     *
     * - 만약 취소 시점에 이미 결제 유효시각이 지나있다면 만료(EXPIRED) 상태로 강제 전환합니다.
     * - 상태 변경에 따른 아웃박스 이벤트(진입 슬롯 해제 요청)를 생성합니다.
     * - DB 트랜잭션이 성공적으로 커밋되면 연동된 Redis의 좌석 선점(Hold)도 즉시 비동기적으로 해제합니다.
     *
     * @param reservationId 예매 ID
     * @param userId 요청자 ID
     * @return 취소 완료된 예매 정보 Dto
     */
    @Transactional
    public ReservationResponseDto cancel(Long reservationId, Long userId) {
        Reservation reservation = findOwnedReservation(reservationId, userId);
        LocalDateTime now = LocalDateTime.now();

        // 만료 시간이 이미 지난 경우
        if (reservation.getStatus() == ReservationStatus.PENDING_PAYMENT
                && !reservation.getExpiresAt().isAfter(now)) {
            reservation.expire(now);
            entrySlotReleaseOutboxWriter.append(
                    reservation,
                    EntrySlotReleaseReason.RESERVATION_EXPIRED,
                    now
            );
        } else {
            // 정상 취소
            try {
                reservation.cancel();
                entrySlotReleaseOutboxWriter.append(
                        reservation,
                        EntrySlotReleaseReason.RESERVATION_CANCELED,
                        now
                );
            } catch (IllegalStateException exception) {
                throw new CustomException(ErrorCode.INVALID_RESERVATION_STATE);
            }
        }

        // 트랜잭션 커밋 완료 후 Redis의 선점을 비동기 해제 처리 등록
        holdReleaseService.releaseAfterCommit(reservation.getHoldId());
        return ReservationResponseDto.from(reservation);
    }

    /**
     * 예매 건에 대해 최종 결제(Payment) 처리를 연동 요청합니다.
     *
     * - 예매 상태를 결제 대기(PENDING_PAYMENT)에서 결제 중(PAYING) 상태로 안전하게 전환(낙관적 락 사용을 위해 select ... for update)합니다.
     * - 비동기 결제 처리를 위해 카프카에 전송할 Outbox 레코드를 데이터베이스에 추가 기입합니다.
     *
     * @param reservationId 예매 ID
     * @param userId 요청자 ID
     * @return 결제 요청이 수락된 예매 정보 Dto
     * @throws CustomException 예매를 찾을 수 없거나 결제 기한이 이미 만료된 경우
     */
    @Transactional
    public PaymentRequestResponseDto requestPayment(
            Long reservationId,
            Long userId
    ) {
        return businessMetrics.record("reservation.payment.request", "real", () -> {
            // 동시성 처리를 위해 행 레벨 락 쿼리 수행
            Reservation reservation = reservationRepository
                    .findByIdAndUserIdForUpdate(reservationId, userId)
                    .orElseThrow(() -> new CustomException(ErrorCode.RESERVATION_NOT_FOUND));
            LocalDateTime requestedAt = LocalDateTime.now();
            String paymentId = UUID.randomUUID().toString();

            boolean requested;
            try {
                requested = reservation.requestPayment(paymentId, requestedAt);
            } catch (IllegalStateException exception) {
                if (reservation.getStatus() == ReservationStatus.PENDING_PAYMENT
                        && !reservation.getExpiresAt().isAfter(requestedAt)) {
                    throw new CustomException(ErrorCode.RESERVATION_PAYMENT_EXPIRED, exception);
                }
                throw new CustomException(ErrorCode.INVALID_RESERVATION_STATE, exception);
            }

            if (requested) {
                // 결제 요청 이벤트를 동일한 DB 트랜잭션 내에서 Outbox 테이블에 추가 저장
                paymentRequestOutboxWriter.append(reservation, requestedAt);
            }
            return PaymentRequestResponseDto.from(reservation);

        });
    }

    /**
     * 선점된 좌석이 여전히 사용 가능한 상태인지 DB 상에서 한 번 더 크로스 체크합니다.
     */
    private List<Seat> findReservationSeats(SeatHold hold) {
        if (hold.seatIds().isEmpty()) {
            throw new CustomException(ErrorCode.RESERVATION_SEAT_MISMATCH);
        }

        List<Seat> seats = seatRepository.findAllByIdIn(hold.seatIds());
        if (seats.size() != hold.seatIds().size()) {
            throw new CustomException(ErrorCode.RESERVATION_SEAT_MISMATCH);
        }

        boolean invalidSeat = seats.stream().anyMatch(seat ->
                !seat.getSection().getSchedule().getId().equals(hold.scheduleId())
                        || seat.getStatus() != SeatStatus.AVAILABLE
        );
        if (invalidSeat) {
            throw new CustomException(ErrorCode.RESERVATION_SEAT_MISMATCH);
        }
        return seats;
    }

    /**
     * 로그인 사용자 본인이 소유한 예매 레코드인지 소유권을 검증합니다.
     */
    private Reservation findOwnedReservation(Long reservationId, Long userId) {
        return reservationRepository.findByIdAndUserId(reservationId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESERVATION_NOT_FOUND));
    }

    /**
     * 조회 시점에 결제 기한을 체크하여 초과한 경우 예매 만료 처리하고 이벤트를 아웃박스에 저장하며 Redis 선점을 취소하는 흐름을 태웁니다.
     */
    private void expireIfNeeded(Reservation reservation, LocalDateTime now) {
        if (reservation.getStatus() == ReservationStatus.PENDING_PAYMENT
                && !reservation.getExpiresAt().isAfter(now)) {
            reservation.expire(now);
            entrySlotReleaseOutboxWriter.append(
                    reservation,
                    EntrySlotReleaseReason.RESERVATION_EXPIRED,
                    now
            );
            holdReleaseService.releaseAfterCommit(reservation.getHoldId());
        }
    }
    private String mode(SeatHold hold) {
        if (hold == null
                || hold.practiceSessionId() == null
                || hold.practiceSessionId().isBlank()) {
            return "real";
        }
        return "practice";
    }

}

