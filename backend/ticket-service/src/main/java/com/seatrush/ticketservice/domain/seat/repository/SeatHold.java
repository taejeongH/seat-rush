package com.seatrush.ticketservice.domain.seat.repository;

import java.time.Instant;
import java.util.List;

public record SeatHold(
        String holdId,
        Long scheduleId,
        Long userId,
        String entryTokenId,
        List<Long> seatIds,
        Instant expiresAt
) {
}
