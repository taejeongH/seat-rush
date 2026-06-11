package com.seatrush.queueservice.domain.schedule.event;

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
}
