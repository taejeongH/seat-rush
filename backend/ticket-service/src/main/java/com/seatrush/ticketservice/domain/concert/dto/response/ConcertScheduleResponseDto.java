package com.seatrush.ticketservice.domain.concert.dto.response;

import com.seatrush.ticketservice.domain.concert.entity.ConcertSchedule;
import com.seatrush.ticketservice.domain.concert.entity.ScheduleStatus;

import java.time.LocalDateTime;

public record ConcertScheduleResponseDto(
        Long scheduleId,
        LocalDateTime performanceAt,
        LocalDateTime bookingOpenAt,
        LocalDateTime bookingCloseAt,
        ScheduleStatus status
) {

    public static ConcertScheduleResponseDto from(ConcertSchedule schedule) {
        return new ConcertScheduleResponseDto(
                schedule.getId(),
                schedule.getPerformanceAt(),
                schedule.getBookingOpenAt(),
                schedule.getBookingCloseAt(),
                schedule.getStatus()
        );
    }
}
