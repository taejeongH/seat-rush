package com.seatrush.queueservice.domain.entrytoken.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record EntrySlotReleaseEvent(
        UUID eventId,
        Long reservationId,
        Long scheduleId,
        Long userId,
        String entryTokenId,
        EntrySlotReleaseReason reason,
        LocalDateTime occurredAt,
        String practiceSessionId
) {
}
