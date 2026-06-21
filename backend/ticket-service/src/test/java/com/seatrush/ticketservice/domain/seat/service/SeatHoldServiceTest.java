package com.seatrush.ticketservice.domain.seat.service;

import com.seatrush.ticketservice.common.entrytoken.EntryTokenClaims;
import com.seatrush.ticketservice.common.entrytoken.EntryTokenValidator;
import com.seatrush.ticketservice.common.exception.CustomException;
import com.seatrush.ticketservice.common.response.status.ErrorCode;
import com.seatrush.ticketservice.domain.concert.entity.ConcertSchedule;
import com.seatrush.ticketservice.domain.seat.config.SeatHoldProperties;
import com.seatrush.ticketservice.domain.seat.dto.response.SeatHoldResponseDto;
import com.seatrush.ticketservice.domain.seat.entity.Seat;
import com.seatrush.ticketservice.domain.seat.entity.SeatSection;
import com.seatrush.ticketservice.domain.seat.entity.SeatStatus;
import com.seatrush.ticketservice.domain.seat.repository.SeatHoldRedisRepository;
import com.seatrush.ticketservice.domain.seat.repository.SeatHoldResult;
import com.seatrush.ticketservice.domain.seat.repository.SeatRepository;
import com.seatrush.ticketservice.domain.seatlayout.service.SeatLayoutQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 좌석 선점 정책과 entryToken 연동을 검증합니다.
 */
class SeatHoldServiceTest {

    private SeatRepository seatRepository;
    private SeatLayoutQueryService layoutQueryService;
    private SeatHoldRedisRepository holdRedisRepository;
    private EntryTokenValidator entryTokenValidator;
    private SeatHoldService service;

    @BeforeEach
    void setUp() {
        seatRepository = mock(SeatRepository.class);
        layoutQueryService = mock(SeatLayoutQueryService.class);
        holdRedisRepository = mock(SeatHoldRedisRepository.class);
        entryTokenValidator = mock(EntryTokenValidator.class);
        service = new SeatHoldService(
                seatRepository,
                layoutQueryService,
                holdRedisRepository,
                entryTokenValidator,
                new SeatHoldProperties(Duration.ofMinutes(5), 4),
                new com.seatrush.ticketservice.common.metrics.BusinessMetrics(
                        new io.micrometer.core.instrument.simple.SimpleMeterRegistry()
                )
        );
    }

    /**
     * 모든 좌석이 사용 가능하면 하나의 hold로 전체 좌석을 선점합니다.
     */
    @Test
    void holdAllRequestedSeatsWhenEverySeatIsAvailable() {
        EntryTokenClaims claims = claims();
        List<Seat> seats = List.of(availableSeat(101L, 1L), availableSeat(102L, 1L));
        when(seatRepository.findAllByIdIn(List.of(101L, 102L)))
                .thenReturn(seats);
        when(holdRedisRepository.hold(any(), anyLong()))
                .thenReturn(SeatHoldResult.held());

        SeatHoldResponseDto response = service.hold(
                1L,
                claims,
                List.of(102L, 101L)
        );

        assertThat(response.scheduleId()).isEqualTo(1L);
        assertThat(response.seatIds()).containsExactly(101L, 102L);

        ArgumentCaptor<com.seatrush.ticketservice.domain.seat.repository.SeatHold> captor =
                ArgumentCaptor.forClass(com.seatrush.ticketservice.domain.seat.repository.SeatHold.class);
        verify(holdRedisRepository).hold(captor.capture(), anyLong());
        assertThat(captor.getValue().entryTokenId()).isEqualTo("jti-1");
        assertThat(captor.getValue().seatSectionIds())
                .containsEntry(101L, 10L)
                .containsEntry(102L, 10L);
    }

    /**
     * 요청 좌석 중 하나라도 이미 선점되어 있으면 전체 요청을 실패 처리합니다.
     */
    @Test
    void rejectEntireRequestWhenAnySeatIsAlreadyHeld() {
        EntryTokenClaims claims = claims();
        List<Seat> seats = List.of(availableSeat(101L, 1L), availableSeat(102L, 1L));
        when(seatRepository.findAllByIdIn(List.of(101L, 102L)))
                .thenReturn(seats);
        when(holdRedisRepository.hold(any(), anyLong()))
                .thenReturn(SeatHoldResult.unavailable(102L));

        assertThatThrownBy(() -> service.hold(
                1L,
                claims,
                List.of(101L, 102L)
        ))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SEAT_NOT_AVAILABLE);
    }

    /**
     * 설정한 최대 좌석 수를 초과한 선점 요청을 거부합니다.
     */
    @Test
    void rejectRequestExceedingMaximumSeatCount() {
        assertThatThrownBy(() -> service.hold(
                1L,
                claims(),
                List.of(1L, 2L, 3L, 4L, 5L)
        ))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SEAT_HOLD_LIMIT_EXCEEDED);
    }

    private EntryTokenClaims claims() {
        return new EntryTokenClaims(
                "jti-1",
                10L,
                1L,
                Instant.now().plusSeconds(600)
        );
    }

    private Seat availableSeat(Long seatId, Long scheduleId) {
        ConcertSchedule schedule = mock(ConcertSchedule.class);
        SeatSection section = mock(SeatSection.class);
        Seat seat = mock(Seat.class);

        when(schedule.getId()).thenReturn(scheduleId);
        when(section.getId()).thenReturn(10L);
        when(section.getSchedule()).thenReturn(schedule);
        when(seat.getId()).thenReturn(seatId);
        when(seat.getSection()).thenReturn(section);
        when(seat.getStatus()).thenReturn(SeatStatus.AVAILABLE);
        return seat;
    }
}
