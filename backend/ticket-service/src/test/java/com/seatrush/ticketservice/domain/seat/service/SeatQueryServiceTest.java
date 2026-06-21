package com.seatrush.ticketservice.domain.seat.service;

import com.seatrush.ticketservice.common.entrytoken.EntryTokenClaims;
import com.seatrush.ticketservice.common.entrytoken.EntryTokenValidator;
import com.seatrush.ticketservice.common.metrics.BusinessMetrics;
import com.seatrush.ticketservice.domain.concert.service.ConcertQueryService;
import com.seatrush.ticketservice.domain.seat.dto.response.SeatResponseDto;
import com.seatrush.ticketservice.domain.seat.entity.SeatStatus;
import com.seatrush.ticketservice.domain.seat.repository.SeatRepository;
import com.seatrush.ticketservice.domain.seat.repository.SeatSectionRepository;
import com.seatrush.ticketservice.domain.seat.repository.projection.SeatQueryProjection;
import com.seatrush.ticketservice.domain.seatlayout.dto.response.SeatLayoutSeatResponseDto;
import com.seatrush.ticketservice.domain.seatlayout.repository.projection.SeatLayoutSeatQueryProjection;
import com.seatrush.ticketservice.domain.seatlayout.service.SeatLayoutQueryService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 좌석 목록 조회에서 소속 조건을 포함한 단일 조회 경로를 검증합니다.
 */
class SeatQueryServiceTest {

    private ConcertQueryService concertQueryService;
    private SeatSectionRepository sectionRepository;
    private SeatRepository seatRepository;
    private SeatHoldService seatHoldService;
    private SeatLayoutQueryService layoutQueryService;
    private EntryTokenValidator entryTokenValidator;
    private SeatQueryService service;

    @BeforeEach
    void setUp() {
        concertQueryService = mock(ConcertQueryService.class);
        sectionRepository = mock(SeatSectionRepository.class);
        seatRepository = mock(SeatRepository.class);
        seatHoldService = mock(SeatHoldService.class);
        layoutQueryService = mock(SeatLayoutQueryService.class);
        entryTokenValidator = mock(EntryTokenValidator.class);
        service = new SeatQueryService(
                concertQueryService,
                sectionRepository,
                seatRepository,
                seatHoldService,
                layoutQueryService,
                entryTokenValidator,
                new BusinessMetrics(new SimpleMeterRegistry())
        );
    }

    /**
     * 실제 예매 좌석 조회는 회차 조건을 포함한 조회 한 번으로 구역 소속을 확인합니다.
     */
    @Test
    void getSeatsQueriesSeatsWithScheduleCondition() {
        EntryTokenClaims claims = realClaims();
        SeatQueryProjection seat = realSeat(101L);
        when(seatRepository.findQueryProjectionsBySectionIdAndScheduleId(10L, 1L))
                .thenReturn(List.of(seat));
        when(seatHoldService.findHeldSeats(1L, 10L, List.of(101L), null))
                .thenReturn(Map.of(101L, false));

        List<SeatResponseDto> response = service.getSeats(1L, 10L, claims);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().seatId()).isEqualTo(101L);
        verify(entryTokenValidator).validateSchedule(claims, 1L);
        verify(seatRepository)
                .findQueryProjectionsBySectionIdAndScheduleId(10L, 1L);
        verify(sectionRepository, never()).existsByIdAndScheduleId(10L, 1L);
        verify(concertQueryService, never()).validateScheduleExists(1L);
    }

    /**
     * 연습 모드 좌석 조회는 레이아웃 조건을 포함한 좌석 조회와 토큰 검증만 수행합니다.
     */
    @Test
    void getPracticeSeatsQueriesSeatsWithLayoutCondition() {
        EntryTokenClaims claims = practiceClaims();
        SeatLayoutSeatQueryProjection seat = practiceSeat(201L);
        when(layoutQueryService.getLayoutSeats(20L, 1L)).thenReturn(List.of(seat));
        when(seatHoldService.findHeldSeats(1L, 20L, List.of(201L), "practice-1"))
                .thenReturn(Map.of(201L, true));

        List<SeatLayoutSeatResponseDto> response = service.getPracticeSeats(
                1L,
                20L,
                "practice-1",
                claims
        );

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().seatId()).isEqualTo(201L);
        verify(layoutQueryService).validatePracticeToken(1L, "practice-1", claims);
        verify(layoutQueryService).getLayoutSeats(20L, 1L);
        verify(layoutQueryService, never()).validatePracticeEntry(1L, "practice-1", claims);
    }

    private EntryTokenClaims realClaims() {
        return new EntryTokenClaims("entry-1", 1L, 1L, Instant.now().plusSeconds(600));
    }

    private EntryTokenClaims practiceClaims() {
        return new EntryTokenClaims(
                "entry-2",
                1L,
                1L,
                "practice-1",
                Instant.now().plusSeconds(600)
        );
    }

    private SeatQueryProjection realSeat(Long seatId) {
        return new SeatQueryProjection(seatId, "A", 1, SeatStatus.AVAILABLE);
    }

    private SeatLayoutSeatQueryProjection practiceSeat(Long seatId) {
        return new SeatLayoutSeatQueryProjection(seatId, "P1", 1);
    }
}
