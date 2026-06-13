package com.seatrush.ticketservice.domain.reservation.service;

import com.seatrush.ticketservice.common.entrytoken.EntryTokenClaims;
import com.seatrush.ticketservice.common.exception.CustomException;
import com.seatrush.ticketservice.common.response.status.ErrorCode;
import com.seatrush.ticketservice.domain.reservation.config.ReservationProperties;
import com.seatrush.ticketservice.domain.reservation.dto.response.ReservationResponseDto;
import com.seatrush.ticketservice.domain.reservation.repository.ReservationRepository;
import com.seatrush.ticketservice.domain.seat.repository.SeatHold;
import com.seatrush.ticketservice.domain.seat.service.SeatHoldService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Redis 좌석 선점 처리와 DB 예매 생성을 짧은 트랜잭션 경계로 조합합니다.
 */
@Service
public class ReservationFacade {

    private final ReservationRepository reservationRepository;
    private final SeatHoldService seatHoldService;
    private final ReservationService reservationService;
    private final ReservationProperties properties;

    public ReservationFacade(
            ReservationRepository reservationRepository,
            SeatHoldService seatHoldService,
            ReservationService reservationService,
            ReservationProperties properties
    ) {
        this.reservationRepository = reservationRepository;
        this.seatHoldService = seatHoldService;
        this.reservationService = reservationService;
        this.properties = properties;
    }

    /**
     * 트랜잭션 시작 전에 hold를 재검증하고 결제 기한까지 TTL을 연장합니다.
     */
    public ReservationResponseDto create(String holdId, EntryTokenClaims claims) {
        if (reservationRepository.existsByHoldId(holdId)) {
            throw new CustomException(ErrorCode.RESERVATION_ALREADY_EXISTS);
        }

        Instant expiresAtInstant = Instant.now().plus(properties.paymentTimeout());
        SeatHold hold = seatHoldService.extendForReservation(
                holdId,
                claims,
                properties.paymentTimeout(),
                expiresAtInstant
        );
        LocalDateTime expiresAt = LocalDateTime.ofInstant(
                expiresAtInstant,
                ZoneId.systemDefault()
        );

        return reservationService.create(hold, claims.userId(), expiresAt);
    }
}
