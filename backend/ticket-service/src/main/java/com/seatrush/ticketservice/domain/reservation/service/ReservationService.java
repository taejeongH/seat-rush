package com.seatrush.ticketservice.domain.reservation.service;

import com.seatrush.ticketservice.common.exception.CustomException;
import com.seatrush.ticketservice.common.response.status.ErrorCode;
import com.seatrush.ticketservice.domain.auth.entity.User;
import com.seatrush.ticketservice.domain.auth.repository.UserRepository;
import com.seatrush.ticketservice.domain.reservation.dto.response.ReservationResponseDto;
import com.seatrush.ticketservice.domain.reservation.dto.response.PaymentRequestResponseDto;
import com.seatrush.ticketservice.domain.reservation.entity.Reservation;
import com.seatrush.ticketservice.domain.reservation.entity.ReservationStatus;
import com.seatrush.ticketservice.domain.reservation.event.publisher.PaymentRequestOutboxWriter;
import com.seatrush.ticketservice.domain.reservation.repository.ReservationRepository;
import com.seatrush.ticketservice.domain.seat.entity.Seat;
import com.seatrush.ticketservice.domain.seat.entity.SeatStatus;
import com.seatrush.ticketservice.domain.seat.repository.SeatHold;
import com.seatrush.ticketservice.domain.seat.repository.SeatRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 좌석 선점을 기반으로 예매를 생성하고 상태와 만료 시간을 관리합니다.
 */
@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final SeatRepository seatRepository;
    private final ReservationHoldReleaseService holdReleaseService;
    private final PaymentRequestOutboxWriter paymentRequestOutboxWriter;

    public ReservationService(
            ReservationRepository reservationRepository,
            UserRepository userRepository,
            SeatRepository seatRepository,
            ReservationHoldReleaseService holdReleaseService,
            PaymentRequestOutboxWriter paymentRequestOutboxWriter
    ) {
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
        this.seatRepository = seatRepository;
        this.holdReleaseService = holdReleaseService;
        this.paymentRequestOutboxWriter = paymentRequestOutboxWriter;
    }

    /**
     * 유효한 hold의 좌석과 가격을 저장하고 결제 대기 예매를 생성합니다.
     */
    @Transactional
    public ReservationResponseDto create(
            SeatHold hold,
            Long userId,
            LocalDateTime expiresAt
    ) {
        if (reservationRepository.existsByHoldId(hold.holdId())) {
            throw new CustomException(ErrorCode.RESERVATION_ALREADY_EXISTS);
        }

        List<Seat> seats = findReservationSeats(hold);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Reservation reservation = Reservation.create(
                user,
                seats.getFirst().getSection().getSchedule(),
                hold.holdId(),
                seats,
                expiresAt
        );

        try {
            reservationRepository.saveAndFlush(reservation);
        } catch (DataIntegrityViolationException exception) {
            throw new CustomException(ErrorCode.RESERVATION_ALREADY_EXISTS);
        }

        return ReservationResponseDto.from(reservation);
    }

    /**
     * 로그인 사용자가 소유한 예매를 조회하고 지연된 만료 상태를 즉시 반영합니다.
     */
    @Transactional
    public ReservationResponseDto get(Long reservationId, Long userId) {
        Reservation reservation = findOwnedReservation(reservationId, userId);
        expireIfNeeded(reservation, LocalDateTime.now());
        return ReservationResponseDto.from(reservation);
    }

    /**
     * 결제 대기 예매를 취소하고 DB 커밋 후 좌석 선점을 해제합니다.
     */
    @Transactional
    public ReservationResponseDto cancel(Long reservationId, Long userId) {
        Reservation reservation = findOwnedReservation(reservationId, userId);
        LocalDateTime now = LocalDateTime.now();

        if (reservation.getStatus() == ReservationStatus.PENDING_PAYMENT
                && !reservation.getExpiresAt().isAfter(now)) {
            reservation.expire(now);
        } else {
            try {
                reservation.cancel();
            } catch (IllegalStateException exception) {
                throw new CustomException(ErrorCode.INVALID_RESERVATION_STATE);
            }
        }

        holdReleaseService.releaseAfterCommit(reservation.getHoldId());
        return ReservationResponseDto.from(reservation);
    }

    /**
     * 예매를 결제 처리 중 상태로 변경하고 결제 요청 이벤트를 Outbox에 저장합니다.
     */
    @Transactional
    public PaymentRequestResponseDto requestPayment(
            Long reservationId,
            Long userId
    ) {
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
            paymentRequestOutboxWriter.append(reservation, requestedAt);
        }
        return PaymentRequestResponseDto.from(reservation);
    }

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

    private Reservation findOwnedReservation(Long reservationId, Long userId) {
        return reservationRepository.findByIdAndUserId(reservationId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESERVATION_NOT_FOUND));
    }

    private void expireIfNeeded(Reservation reservation, LocalDateTime now) {
        if (reservation.getStatus() == ReservationStatus.PENDING_PAYMENT
                && !reservation.getExpiresAt().isAfter(now)) {
            reservation.expire(now);
            holdReleaseService.releaseAfterCommit(reservation.getHoldId());
        }
    }
}
