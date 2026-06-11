package com.seatrush.ticketservice.domain.concert.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record ScheduleCreateRequestDto(
        @NotNull LocalDateTime performanceAt,
        @NotNull LocalDateTime bookingOpenAt,
        @NotNull LocalDateTime bookingCloseAt
) {
}
