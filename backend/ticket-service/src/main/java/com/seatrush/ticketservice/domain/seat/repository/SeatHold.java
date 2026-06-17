package com.seatrush.ticketservice.domain.seat.repository;

import java.time.Instant;
import java.util.List;

public record SeatHold(
        String holdId,
        Long scheduleId,
        Long userId,
        String entryTokenId,
        String practiceSessionId,
        List<Long> seatIds,
        Instant expiresAt
) {

    public SeatHold(
            String holdId,
            Long scheduleId,
            Long userId,
            String entryTokenId,
            List<Long> seatIds,
            Instant expiresAt
    ) {
        this(holdId, scheduleId, userId, entryTokenId, null, seatIds, expiresAt);
    }

    public boolean practiceMode() {
        return practiceSessionId != null && !practiceSessionId.isBlank();
    }
}
