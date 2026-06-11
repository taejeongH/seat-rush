package com.seatrush.ticketservice.domain.concert.service;

import com.seatrush.ticketservice.common.exception.CustomException;
import com.seatrush.ticketservice.common.response.status.ErrorCode;
import com.seatrush.ticketservice.domain.concert.dto.request.ScheduleCreateRequestDto;
import com.seatrush.ticketservice.domain.concert.dto.request.ScheduleUpdateRequestDto;
import com.seatrush.ticketservice.domain.concert.dto.response.ConcertScheduleResponseDto;
import com.seatrush.ticketservice.domain.concert.dto.response.ScheduleSyncResponseDto;
import com.seatrush.ticketservice.domain.concert.entity.Concert;
import com.seatrush.ticketservice.domain.concert.entity.ConcertSchedule;
import com.seatrush.ticketservice.domain.concert.entity.ScheduleStatus;
import com.seatrush.ticketservice.domain.concert.event.model.ScheduleEventType;
import com.seatrush.ticketservice.domain.concert.event.publisher.ScheduleEventOutboxWriter;
import com.seatrush.ticketservice.domain.concert.repository.ConcertRepository;
import com.seatrush.ticketservice.domain.concert.repository.ConcertScheduleRepository;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 회차 생성, 수정, 취소와 상태 이벤트 기록을 하나의 트랜잭션으로 처리합니다.
 */
@Service
public class ScheduleCommandService {

    private final ConcertRepository concertRepository;
    private final ConcertScheduleRepository scheduleRepository;
    private final ScheduleEventOutboxWriter eventOutboxWriter;

    public ScheduleCommandService(
            ConcertRepository concertRepository,
            ConcertScheduleRepository scheduleRepository,
            ScheduleEventOutboxWriter eventOutboxWriter
    ) {
        this.concertRepository = concertRepository;
        this.scheduleRepository = scheduleRepository;
        this.eventOutboxWriter = eventOutboxWriter;
    }

    /**
     * 공연에 새로운 회차를 생성하고 CREATED 이벤트를 Outbox에 기록합니다.
     */
    @Transactional
    public ConcertScheduleResponseDto create(
            Long concertId,
            ScheduleCreateRequestDto request
    ) {
        Concert concert = findConcert(concertId);

        try {
            ConcertSchedule schedule = ConcertSchedule.create(
                    concert,
                    request.performanceAt(),
                    request.bookingOpenAt(),
                    request.bookingCloseAt()
            );
            scheduleRepository.saveAndFlush(schedule);
            eventOutboxWriter.append(schedule, ScheduleEventType.CREATED);
            return ConcertScheduleResponseDto.from(schedule);
        } catch (IllegalArgumentException exception) {
            throw new CustomException(ErrorCode.INVALID_SCHEDULE_PERIOD);
        }
    }

    /**
     * 회차 정보를 수정하고 UPDATED 이벤트를 Outbox에 기록합니다.
     */
    @Transactional
    public ConcertScheduleResponseDto update(
            Long scheduleId,
            ScheduleUpdateRequestDto request
    ) {
        ConcertSchedule schedule = findSchedule(scheduleId);
        validateUpdateStatus(request.status());

        try {
            schedule.update(
                    valueOrDefault(request.performanceAt(), schedule.getPerformanceAt()),
                    valueOrDefault(request.bookingOpenAt(), schedule.getBookingOpenAt()),
                    valueOrDefault(request.bookingCloseAt(), schedule.getBookingCloseAt()),
                    valueOrDefault(request.status(), schedule.getStatus())
            );
            scheduleRepository.flush();
            eventOutboxWriter.append(schedule, ScheduleEventType.UPDATED);
            return ConcertScheduleResponseDto.from(schedule);
        } catch (IllegalArgumentException exception) {
            throw new CustomException(ErrorCode.INVALID_SCHEDULE_PERIOD);
        } catch (IllegalStateException exception) {
            throw new CustomException(ErrorCode.CANCELED_SCHEDULE);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new CustomException(ErrorCode.SCHEDULE_UPDATE_CONFLICT);
        }
    }

    /**
     * 회차를 삭제하지 않고 취소 상태로 변경한 뒤 CANCELED 이벤트를 Outbox에 기록합니다.
     */
    @Transactional
    public ConcertScheduleResponseDto cancel(Long scheduleId) {
        ConcertSchedule schedule = findSchedule(scheduleId);

        try {
            schedule.cancel();
            scheduleRepository.flush();
            eventOutboxWriter.append(schedule, ScheduleEventType.CANCELED);
            return ConcertScheduleResponseDto.from(schedule);
        } catch (IllegalStateException exception) {
            throw new CustomException(ErrorCode.CANCELED_SCHEDULE);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new CustomException(ErrorCode.SCHEDULE_UPDATE_CONFLICT);
        }
    }

    /**
     * 현재 모든 회차 상태를 Queue Service에 다시 전달하도록 동기화 이벤트를 기록합니다.
     */
    @Transactional
    public ScheduleSyncResponseDto synchronizeAll() {
        List<ConcertSchedule> schedules = scheduleRepository.findAll(Sort.by("id"));
        schedules.forEach(schedule ->
                eventOutboxWriter.append(schedule, ScheduleEventType.SYNCHRONIZED)
        );
        return new ScheduleSyncResponseDto(schedules.size());
    }

    /**
     * 공연을 조회하고 존재하지 않으면 공연 조회 예외를 발생시킵니다.
     */
    private Concert findConcert(Long concertId) {
        return concertRepository.findById(concertId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONCERT_NOT_FOUND));
    }

    /**
     * 회차를 조회하고 존재하지 않으면 회차 조회 예외를 발생시킵니다.
     */
    private ConcertSchedule findSchedule(Long scheduleId) {
        return scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new CustomException(ErrorCode.SCHEDULE_NOT_FOUND));
    }

    /**
     * 회차 취소는 전용 API를 사용하도록 수정 요청의 CANCELED 상태를 거부합니다.
     */
    private void validateUpdateStatus(ScheduleStatus status) {
        if (status == ScheduleStatus.CANCELED) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    /**
     * 수정 값이 없으면 기존 값을 유지합니다.
     */
    private static <T> T valueOrDefault(T value, T defaultValue) {
        return value == null ? defaultValue : value;
    }
}
