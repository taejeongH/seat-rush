package com.seatrush.ticketservice.domain.practice.service;

import com.seatrush.ticketservice.common.entrytoken.EntryTokenClaims;
import com.seatrush.ticketservice.common.exception.CustomException;
import com.seatrush.ticketservice.common.response.status.ErrorCode;
import com.seatrush.ticketservice.domain.practice.dto.PracticePaymentPreparationResponseDto;
import com.seatrush.ticketservice.domain.practice.dto.PracticePaymentResponseDto;
import com.seatrush.ticketservice.domain.practice.event.PracticeEntrySlotReleasePublisher;
import com.seatrush.ticketservice.domain.practice.repository.PracticeReservationRedisRepository;
import com.seatrush.ticketservice.domain.practice.repository.PracticeReservationState;
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

@Service
public class PracticeReservationService {

    private static final Duration SESSION_RESULT_TTL = Duration.ofHours(3);

    private final PracticeReservationRedisRepository repository;
    private final SeatHoldService seatHoldService;
    private final SeatRepository seatRepository;
    private final SeatLayoutSeatRepository layoutSeatRepository;
    private final ReservationProperties reservationProperties;
    private final PracticeEntrySlotReleasePublisher entrySlotReleasePublisher;

    public PracticeReservationService(
            PracticeReservationRedisRepository repository,
            SeatHoldService seatHoldService,
            SeatRepository seatRepository,
            SeatLayoutSeatRepository layoutSeatRepository,
            ReservationProperties reservationProperties,
            PracticeEntrySlotReleasePublisher entrySlotReleasePublisher
    ) {
        this.repository = repository;
        this.seatHoldService = seatHoldService;
        this.seatRepository = seatRepository;
        this.layoutSeatRepository = layoutSeatRepository;
        this.reservationProperties = reservationProperties;
        this.entrySlotReleasePublisher = entrySlotReleasePublisher;
    }

    public ReservationResponseDto create(String holdId, EntryTokenClaims claims) {
        validatePracticeMode(claims);
        Instant expiresAtInstant = Instant.now().plus(reservationProperties.paymentTimeout());
        SeatHold hold = seatHoldService.extendForReservation(
                holdId,
                claims,
                reservationProperties.paymentTimeout(),
                expiresAtInstant
        );
        List<ReservationSeatResponseDto> seats = findSeats(hold, claims.practiceMode());
        BigDecimal totalAmount = seats.stream()
                .map(ReservationSeatResponseDto::price)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Long reservationId = repository.nextReservationId(claims.practiceSessionId());
        LocalDateTime expiresAt = LocalDateTime.ofInstant(
                expiresAtInstant,
                ZoneId.systemDefault()
        );

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
        repository.save(state, SESSION_RESULT_TTL);
        return toReservationResponse(state);
    }

    public ReservationResponseDto get(
            String practiceSessionId,
            Long reservationId,
            Long userId
    ) {
        PracticeReservationState state = findOwned(practiceSessionId, reservationId, userId);
        return toReservationResponse(state);
    }

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
        repository.save(updated, SESSION_RESULT_TTL);
        return new PaymentRequestResponseDto(
                updated.reservationId(),
                updated.paymentId(),
                updated.status()
        );
    }

    public PracticePaymentPreparationResponseDto getPayment(
            String practiceSessionId,
            String paymentId,
            Long userId
    ) {
        PracticeReservationState state = findOwnedByPayment(practiceSessionId, paymentId, userId);
        return PracticePaymentPreparationResponseDto.ready(state.paymentId());
    }

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
        repository.save(updated, SESSION_RESULT_TTL);
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

    public void deleteSession(String practiceSessionId) {
        repository.deleteSession(practiceSessionId);
    }

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
