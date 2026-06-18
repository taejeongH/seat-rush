package com.seatrush.ticketservice.domain.practice.reservation.service;

import com.seatrush.ticketservice.common.entrytoken.EntryTokenClaims;
import com.seatrush.ticketservice.common.exception.CustomException;
import com.seatrush.ticketservice.common.response.status.ErrorCode;
import com.seatrush.ticketservice.domain.practice.reservation.config.PracticeProperties;
import com.seatrush.ticketservice.domain.practice.reservation.dto.PracticePaymentPreparationResponseDto;
import com.seatrush.ticketservice.domain.practice.reservation.dto.PracticePaymentResponseDto;
import com.seatrush.ticketservice.domain.practice.reservation.event.PracticeEntrySlotReleasePublisher;
import com.seatrush.ticketservice.domain.practice.reservation.repository.PracticeReservationRedisRepository;
import com.seatrush.ticketservice.domain.practice.reservation.repository.PracticeReservationState;
import com.seatrush.ticketservice.domain.reservation.config.ReservationProperties;
import com.seatrush.ticketservice.domain.reservation.dto.response.PaymentRequestResponseDto;
import com.seatrush.ticketservice.domain.reservation.dto.response.ReservationResponseDto;
import com.seatrush.ticketservice.domain.reservation.dto.response.ReservationSeatResponseDto;
import com.seatrush.ticketservice.domain.reservation.entity.ReservationStatus;
import com.seatrush.ticketservice.domain.reservation.event.model.EntrySlotReleaseReason;
import com.seatrush.ticketservice.domain.seat.entity.Seat;
import com.seatrush.ticketservice.domain.seat.repository.SeatHold;
import com.seatrush.ticketservice.domain.seat.repository.SeatRepository;
import com.seatrush.ticketservice.domain.seat.service.SeatHoldService;
import com.seatrush.ticketservice.domain.seatlayout.entity.SeatLayoutSeat;
import com.seatrush.ticketservice.domain.seatlayout.repository.SeatLayoutSeatRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * 연습 모드(Practice Mode)에서의 가상 예매 파이프라인을 시뮬레이션하는 특수 서비스 클래스입니다.
 * 
 * 실제 DB에 예매 레코드를 쌓지 않고, Redis Hash 캐시 공간을 일종의 인메모리 DB 샌드박스로 사용하여
 * 매우 빠른 대량 요청에도 서비스 전체에 병목이 생기지 않도록 가상 예매의 라이프사이클(생성 -> 결제 중 -> 완료/실패)을 시뮬레이션합니다.
 */
@Service
public class PracticeReservationService {

    private final PracticeReservationRedisRepository repository;
    private final SeatHoldService seatHoldService;
    private final SeatRepository seatRepository;
    private final SeatLayoutSeatRepository layoutSeatRepository;
    private final ReservationProperties reservationProperties;
    private final PracticeProperties practiceProperties;
    private final PracticeEntrySlotReleasePublisher entrySlotReleasePublisher;

    public PracticeReservationService(
            PracticeReservationRedisRepository repository,
            SeatHoldService seatHoldService,
            SeatRepository seatRepository,
            SeatLayoutSeatRepository layoutSeatRepository,
            ReservationProperties reservationProperties,
            PracticeProperties practiceProperties,
            PracticeEntrySlotReleasePublisher entrySlotReleasePublisher
    ) {
        this.repository = repository;
        this.seatHoldService = seatHoldService;
        this.seatRepository = seatRepository;
        this.layoutSeatRepository = layoutSeatRepository;
        this.reservationProperties = reservationProperties;
        this.practiceProperties = practiceProperties;
        this.entrySlotReleasePublisher = entrySlotReleasePublisher;
    }

    /**
     * 연습 모드에서의 가상 예매 데이터를 생성하여 Redis에 저장합니다.
     * 
     * RDB 트랜잭션 없이 Redis 해시에 정보를 영속화하고 가상 ReservationId를 발급합니다.
     *
     * @param holdId 좌석 선점 UUID
     * @param claims 대기열 토큰
     * @return 가상 예매 생성 정보 Dto
     */
    public ReservationResponseDto create(String holdId, EntryTokenClaims claims) {
        validatePracticeMode(claims);
        Instant expiresAtInstant = Instant.now().plus(reservationProperties.paymentTimeout());
        
        // Redis 상의 선점 만료 기한을 연장
        SeatHold hold = seatHoldService.extendForReservation(
                holdId,
                claims,
                reservationProperties.paymentTimeout(),
                expiresAtInstant
        );
        
        // 좌석 정보 로드 (연습모드 전용 레이아웃 좌석 정보)
        List<ReservationSeatResponseDto> seats = findSeats(hold, claims.practiceMode());
        BigDecimal totalAmount = seats.stream()
                .map(ReservationSeatResponseDto::price)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Redis 시퀀스를 이용해 모조 예매 ID 획득
        Long reservationId = repository.nextReservationId(claims.practiceSessionId());
        LocalDateTime expiresAt = LocalDateTime.ofInstant(
                expiresAtInstant,
                ZoneId.systemDefault()
        );

        // Redis 기반 상태 레코드 생성 및 캐싱
        PracticeReservationState state = new PracticeReservationState(
                reservationId,
                claims.practiceSessionId(),
                hold.scheduleId(),
                claims.userId(),
                hold.holdId(),
                hold.entryTokenId(),
                null,
                ReservationStatus.PENDING_PAYMENT,
                totalAmount,
                expiresAt,
                seats
        );
        repository.save(state, practiceProperties.sessionResultTtl());
        return toReservationResponse(state);
    }

    /**
     * Redis에서 로그인 사용자 소유의 가상 예매 정보를 조회합니다.
     */
    public ReservationResponseDto get(
            String practiceSessionId,
            Long reservationId,
            Long userId
    ) {
        PracticeReservationState state = findOwned(practiceSessionId, reservationId, userId);
        return toReservationResponse(state);
    }

    /**
     * 연습 모드 가상 예매를 결제 처리 중(PAYMENT_PROCESSING) 단계로 전이시킵니다.
     * 
     * @return 결제 요청이 접수된 가상 예매 결과 Dto
     */
    public PaymentRequestResponseDto requestPayment(
            String practiceSessionId,
            Long reservationId,
            Long userId
    ) {
        PracticeReservationState state = findOwned(practiceSessionId, reservationId, userId);
        if (state.status() != ReservationStatus.PENDING_PAYMENT
                && state.status() != ReservationStatus.PAYMENT_PROCESSING) {
            throw new CustomException(ErrorCode.INVALID_RESERVATION_STATE);
        }

        String paymentId = state.paymentId() == null
                ? UUID.randomUUID().toString()
                : state.paymentId();
                
        // 상태를 결제 진행 중 상태로 빌드하여 Redis에 덮어쓰기
        PracticeReservationState updated = new PracticeReservationState(
                state.reservationId(),
                state.practiceSessionId(),
                state.scheduleId(),
                state.userId(),
                state.holdId(),
                state.entryTokenId(),
                paymentId,
                ReservationStatus.PAYMENT_PROCESSING,
                state.totalAmount(),
                state.expiresAt(),
                state.seats()
        );
        repository.save(updated, practiceProperties.sessionResultTtl());
        return new PaymentRequestResponseDto(
                updated.reservationId(),
                updated.paymentId(),
                updated.status()
        );
    }

    /**
     * 외부 PG를 가장한 결제 페이지 진입 준비를 수행합니다.
     */
    public PracticePaymentPreparationResponseDto getPayment(
            String practiceSessionId,
            String paymentId,
            Long userId
    ) {
        PracticeReservationState state = findOwnedByPayment(practiceSessionId, paymentId, userId);
        return PracticePaymentPreparationResponseDto.ready(state.paymentId());
    }

    /**
     * 사용자가 시뮬레이터 결제창에서 최종 확인(혹은 실패)을 눌렀을 때 결제를 확정합니다.
     * 
     * - 성공 시 CONFIRMED, 실패 시 CANCELED 상태로 반영합니다.
     * - 처리 직후 대기열 카프카 메커니즘을 시뮬레이션하기 위해 진입 슬롯 해제 퍼블리셔를 비동기로 호출합니다.
     *
     * @param practiceSessionId 연습 세션 ID
     * @param paymentId 결제 고유 식별 ID
     * @param userId 요청 사용자 ID
     * @param result 결제 결과 코드 (SUCCESS 또는 FAILED)
     * @return 결제 처리 최종 결과 Dto
     */
    public PracticePaymentResponseDto completePayment(
            String practiceSessionId,
            String paymentId,
            Long userId,
            String result
    ) {
        PracticeReservationState state = findOwnedByPayment(practiceSessionId, paymentId, userId);
        if (state.status() != ReservationStatus.PAYMENT_PROCESSING) {
            throw new CustomException(ErrorCode.INVALID_RESERVATION_STATE);
        }

        ReservationStatus nextStatus = "SUCCESS".equals(result)
                ? ReservationStatus.CONFIRMED
                : ReservationStatus.CANCELED;
                
        PracticeReservationState updated = new PracticeReservationState(
                state.reservationId(),
                state.practiceSessionId(),
                state.scheduleId(),
                state.userId(),
                state.holdId(),
                state.entryTokenId(),
                state.paymentId(),
                nextStatus,
                state.totalAmount(),
                state.expiresAt(),
                state.seats()
        );
        repository.save(updated, practiceProperties.sessionResultTtl());
        
        // 대기열 슬롯에서 탈출/해제 시그널 퍼블리싱 (비동기 처리 유도)
        entrySlotReleasePublisher.publish(
                updated,
                "SUCCESS".equals(result)
                        ? EntrySlotReleaseReason.PAYMENT_SUCCESS
                        : EntrySlotReleaseReason.PAYMENT_FAILED
        );
        
        return new PracticePaymentResponseDto(
                updated.paymentId(),
                updated.reservationId(),
                updated.totalAmount(),
                "SUCCESS".equals(result) ? "SUCCESS" : "FAILED",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    /**
     * 세션 종료 또는 리셋 시 Redis의 가상 데이터들을 일괄 정리합니다.
     */
    public void deleteSession(String practiceSessionId) {
        repository.deleteSession(practiceSessionId);
    }

    /**
     * Redis에 캐시된 사용자 소유의 가상 예매 정보를 조회하고 소유권을 체크합니다.
     */
    private PracticeReservationState findOwned(
            String practiceSessionId,
            Long reservationId,
            Long userId
    ) {
        PracticeReservationState state = repository.findByReservationId(
                practiceSessionId,
                reservationId
        );
        if (state == null || !state.userId().equals(userId)) {
            throw new CustomException(ErrorCode.RESERVATION_NOT_FOUND);
        }
        return state;
    }

    /**
     * PG 결제 세션 매칭 시 활용할 가상 예매 정보를 결제 ID 기반으로 조회합니다.
     */
    private PracticeReservationState findOwnedByPayment(
            String practiceSessionId,
            String paymentId,
            Long userId
    ) {
        PracticeReservationState state = repository.findByPaymentId(practiceSessionId, paymentId);
        if (state == null || !state.userId().equals(userId)) {
            throw new CustomException(ErrorCode.RESERVATION_NOT_FOUND);
        }
        return state;
    }

    /**
     * 연습 모드 혹은 실제 모드 구분에 맞춰 타겟 좌석 정보를 로드합니다.
     */
    private List<ReservationSeatResponseDto> findSeats(SeatHold hold, boolean practiceMode) {
        if (practiceMode) {
            return findLayoutSeats(hold);
        }

        List<Seat> seats = seatRepository.findAllByIdIn(hold.seatIds());
        if (seats.size() != hold.seatIds().size()) {
            throw new CustomException(ErrorCode.RESERVATION_SEAT_MISMATCH);
        }
        return seats.stream()
                .map(seat -> new ReservationSeatResponseDto(
                        seat.getId(),
                        seat.getSection().getId(),
                        seat.getSection().getName(),
                        seat.getRowName(),
                        seat.getSeatNumber(),
                        seat.getSection().getPrice()
                ))
                .toList();
    }

    /**
     * 연습모드 전용 레이아웃 좌석 구조 정보(DB)로부터 정보를 채웁니다.
     */
    private List<ReservationSeatResponseDto> findLayoutSeats(SeatHold hold) {
        List<SeatLayoutSeat> seats = layoutSeatRepository.findAllByIdIn(hold.seatIds());
        if (seats.size() != hold.seatIds().size()) {
            throw new CustomException(ErrorCode.RESERVATION_SEAT_MISMATCH);
        }
        return seats.stream()
                .map(seat -> new ReservationSeatResponseDto(
                        seat.getId(),
                        seat.getSection().getId(),
                        seat.getSection().getName(),
                        seat.getRowName(),
                        seat.getSeatNumber(),
                        seat.getSection().getPrice()
                ))
                .toList();
    }

    private ReservationResponseDto toReservationResponse(PracticeReservationState state) {
        return new ReservationResponseDto(
                state.reservationId(),
                state.scheduleId(),
                state.holdId(),
                state.paymentId(),
                state.status(),
                state.totalAmount(),
                state.expiresAt(),
                state.seats()
        );
    }

    private void validatePracticeMode(EntryTokenClaims claims) {
        if (!claims.practiceMode()) {
            throw new CustomException(ErrorCode.INVALID_ENTRY_TOKEN);
        }
    }
}
