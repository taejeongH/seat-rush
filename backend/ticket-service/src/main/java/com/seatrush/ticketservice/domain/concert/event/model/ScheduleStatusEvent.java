package com.seatrush.ticketservice.domain.concert.event.model;

import com.seatrush.ticketservice.domain.concert.entity.ConcertSchedule;
import com.seatrush.ticketservice.domain.concert.entity.ScheduleStatus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record ScheduleStatusEvent(
        UUID eventId,
        ScheduleEventType eventType,
        Long scheduleId,
        ScheduleStatus status,
        LocalDateTime bookingOpenAt,
        LocalDateTime bookingCloseAt,
        long version,
        Instant occurredAt
) {

    public static ScheduleStatusEvent from(
            ConcertSchedule schedule,
            ScheduleEventType eventType
    ) {
        return new ScheduleStatusEvent(
                UUID.randomUUID(),
                eventType,
                schedule.getId(),
                schedule.getStatus(),
                schedule.getBookingOpenAt(),
                schedule.getBookingCloseAt(),
                schedule.getVersion(),
                Instant.now()
        );
    }
}
