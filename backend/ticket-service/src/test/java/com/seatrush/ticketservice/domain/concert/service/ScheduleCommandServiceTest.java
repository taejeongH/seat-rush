package com.seatrush.ticketservice.domain.concert.service;

import com.seatrush.ticketservice.domain.concert.dto.request.ScheduleCreateRequestDto;
import com.seatrush.ticketservice.domain.concert.dto.request.ScheduleUpdateRequestDto;
import com.seatrush.ticketservice.domain.concert.dto.response.ScheduleSyncResponseDto;
import com.seatrush.ticketservice.domain.concert.entity.Concert;
import com.seatrush.ticketservice.domain.concert.entity.ConcertSchedule;
import com.seatrush.ticketservice.domain.concert.entity.ScheduleStatus;
import com.seatrush.ticketservice.domain.concert.event.model.ScheduleEventType;
import com.seatrush.ticketservice.domain.concert.event.publisher.ScheduleEventOutboxWriter;
import com.seatrush.ticketservice.domain.concert.repository.ConcertRepository;
import com.seatrush.ticketservice.domain.concert.repository.ConcertScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 회차 명령과 Outbox 이벤트 기록의 연결을 검증합니다.
 */
class ScheduleCommandServiceTest {

    private ConcertRepository concertRepository;
    private ConcertScheduleRepository scheduleRepository;
    private ScheduleEventOutboxWriter eventOutboxWriter;
    private ScheduleCommandService service;

    @BeforeEach
    void setUp() {
        concertRepository = mock(ConcertRepository.class);
        scheduleRepository = mock(ConcertScheduleRepository.class);
        eventOutboxWriter = mock(ScheduleEventOutboxWriter.class);
        service = new ScheduleCommandService(
                concertRepository,
                scheduleRepository,
                eventOutboxWriter
        );
    }

    /**
     * 회차 생성 시 DB에 반영한 뒤 CREATED 이벤트를 Outbox에 기록합니다.
     */
    @Test
    void appendCreatedEventAfterScheduleCreation() {
        Concert concert = mock(Concert.class);
        when(concertRepository.findById(1L)).thenReturn(Optional.of(concert));
        ScheduleCreateRequestDto request = new ScheduleCreateRequestDto(
                LocalDateTime.of(2026, 7, 11, 19, 0),
                LocalDateTime.of(2026, 7, 1, 10, 0),
                LocalDateTime.of(2026, 7, 10, 18, 0)
        );

        service.create(1L, request);

        ArgumentCaptor<ConcertSchedule> scheduleCaptor = ArgumentCaptor.forClass(ConcertSchedule.class);
        verify(scheduleRepository).saveAndFlush(scheduleCaptor.capture());
        verify(eventOutboxWriter).append(scheduleCaptor.getValue(), ScheduleEventType.CREATED);
    }

    /**
     * 회차 수정 시 변경 내용을 flush한 뒤 UPDATED 이벤트를 Outbox에 기록합니다.
     */
    @Test
    void appendUpdatedEventAfterScheduleUpdate() {
        ConcertSchedule schedule = mock(ConcertSchedule.class);
        LocalDateTime performanceAt = LocalDateTime.of(2026, 7, 11, 19, 0);
        LocalDateTime bookingOpenAt = LocalDateTime.of(2026, 7, 1, 10, 0);
        LocalDateTime bookingCloseAt = LocalDateTime.of(2026, 7, 10, 18, 0);
        when(scheduleRepository.findById(1L)).thenReturn(Optional.of(schedule));
        ScheduleUpdateRequestDto request = new ScheduleUpdateRequestDto(
                performanceAt,
                bookingOpenAt,
                bookingCloseAt,
                ScheduleStatus.BOOKING_OPEN
        );

        service.update(1L, request);

        verify(schedule).update(
                performanceAt,
                bookingOpenAt,
                bookingCloseAt,
                ScheduleStatus.BOOKING_OPEN
        );
        verify(scheduleRepository).flush();
        verify(eventOutboxWriter).append(schedule, ScheduleEventType.UPDATED);
    }

    /**
     * 회차 취소 시 상태를 flush한 뒤 CANCELED 이벤트를 Outbox에 기록합니다.
     */
    @Test
    void appendCanceledEventAfterScheduleCancellation() {
        ConcertSchedule schedule = mock(ConcertSchedule.class);
        when(scheduleRepository.findById(1L)).thenReturn(Optional.of(schedule));

        service.cancel(1L);

        verify(schedule).cancel();
        verify(scheduleRepository).flush();
        verify(eventOutboxWriter).append(schedule, ScheduleEventType.CANCELED);
    }

    /**
     * 전체 동기화 요청 시 모든 회차에 SYNCHRONIZED 이벤트를 기록합니다.
     */
    @Test
    void appendSynchronizationEventForEverySchedule() {
        ConcertSchedule firstSchedule = mock(ConcertSchedule.class);
        ConcertSchedule secondSchedule = mock(ConcertSchedule.class);
        when(scheduleRepository.findAll(org.springframework.data.domain.Sort.by("id")))
                .thenReturn(List.of(firstSchedule, secondSchedule));

        ScheduleSyncResponseDto response = service.synchronizeAll();

        assertThat(response.requestedCount()).isEqualTo(2);
        verify(eventOutboxWriter).append(firstSchedule, ScheduleEventType.SYNCHRONIZED);
        verify(eventOutboxWriter).append(secondSchedule, ScheduleEventType.SYNCHRONIZED);
    }
}
