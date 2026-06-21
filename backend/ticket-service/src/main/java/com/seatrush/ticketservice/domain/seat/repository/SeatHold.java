package com.seatrush.ticketservice.domain.seat.repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record SeatHold(
        String holdId,
        Long scheduleId,
        Long userId,
        String entryTokenId,
        String practiceSessionId,
        List<Long> seatIds,
        Map<Long, Long> seatSectionIds,
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
        this(holdId, scheduleId, userId, entryTokenId, null, seatIds, Map.of(), expiresAt);
    }

    public boolean hasSeatSectionIds() {
        return seatIds.stream().allMatch(seatSectionIds::containsKey);
    }

    public boolean practiceMode() {
        return practiceSessionId != null && !practiceSessionId.isBlank();
    }
}
