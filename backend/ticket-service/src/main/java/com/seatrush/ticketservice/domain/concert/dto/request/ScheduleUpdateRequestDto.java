package com.seatrush.ticketservice.domain.concert.dto.request;

import com.seatrush.ticketservice.domain.concert.entity.ScheduleStatus;

import java.time.LocalDateTime;

public record ScheduleUpdateRequestDto(
        LocalDateTime performanceAt,
        LocalDateTime bookingOpenAt,
        LocalDateTime bookingCloseAt,
        ScheduleStatus status
) {
}
