package com.seatrush.ticketservice.domain.reservation.service;

import com.seatrush.ticketservice.common.entrytoken.EntryTokenClaims;
import com.seatrush.ticketservice.common.exception.CustomException;
import com.seatrush.ticketservice.common.response.status.ErrorCode;
import com.seatrush.ticketservice.domain.reservation.config.ReservationProperties;
import com.seatrush.ticketservice.domain.reservation.repository.ReservationRepository;
import com.seatrush.ticketservice.domain.seat.repository.SeatHold;
import com.seatrush.ticketservice.domain.seat.service.SeatHoldService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Redis hold 선처리와 DB 예매 생성의 실행 순서를 검증합니다.
 */
class ReservationFacadeTest {

    private ReservationRepository reservationRepository;
    private SeatHoldService seatHoldService;
    private ReservationService reservationService;
    private ReservationFacade facade;

    @BeforeEach
    void setUp() {
        reservationRepository = mock(ReservationRepository.class);
        seatHoldService = mock(SeatHoldService.class);
        reservationService = mock(ReservationService.class);
        facade = new ReservationFacade(
                reservationRepository,
                seatHoldService,
                reservationService,
                new ReservationProperties(Duration.ofMinutes(10), 100)
        );
    }

    /**
     * Redis hold를 먼저 검증·연장한 뒤 DB 예매 생성을 호출합니다.
     */
    @Test
    void extendHoldBeforeStartingDatabaseReservationCreation() {
        EntryTokenClaims claims = claims();
        SeatHold hold = hold();
        when(seatHoldService.extendForReservation(
                eq("hold-1"),
                eq(claims),
                eq(Duration.ofMinutes(10)),
                any(Instant.class)
        )).thenReturn(hold);

        facade.create("hold-1", claims);

        var order = inOrder(seatHoldService, reservationService);
        order.verify(seatHoldService).extendForReservation(
                eq("hold-1"),
                eq(claims),
                eq(Duration.ofMinutes(10)),
                any(Instant.class)
        );
        order.verify(reservationService).create(
                eq(hold),
                eq(10L),
                any(LocalDateTime.class)
        );
    }

    /**
     * 이미 생성된 예매는 Redis TTL을 연장하지 않고 즉시 거부합니다.
     */
    @Test
    void rejectExistingReservationBeforeExtendingHold() {
        when(reservationRepository.existsByHoldId("hold-1")).thenReturn(true);

        assertThatThrownBy(() -> facade.create("hold-1", claims()))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RESERVATION_ALREADY_EXISTS);
        verifyNoInteractions(seatHoldService, reservationService);
    }

    private EntryTokenClaims claims() {
        return new EntryTokenClaims(
                "jti-1",
                10L,
                1L,
                Instant.now().plusSeconds(600)
        );
    }

    private SeatHold hold() {
        return new SeatHold(
                "hold-1",
                1L,
                10L,
                "jti-1",
                List.of(101L),
                Instant.now().plusSeconds(600)
        );
    }
}
