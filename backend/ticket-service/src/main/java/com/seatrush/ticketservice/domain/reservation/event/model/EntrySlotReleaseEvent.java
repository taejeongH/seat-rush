package com.seatrush.ticketservice.domain.reservation.event.model;

import com.seatrush.ticketservice.domain.reservation.entity.Reservation;

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

    public static EntrySlotReleaseEvent from(
            Reservation reservation,
            EntrySlotReleaseReason reason,
            LocalDateTime occurredAt
    ) {
        return new EntrySlotReleaseEvent(
                UUID.randomUUID(),
                reservation.getId(),
                reservation.getSchedule().getId(),
                reservation.getUser().getId(),
                reservation.getEntryTokenId(),
                reason,
                occurredAt,
                null
        );
    }
}
