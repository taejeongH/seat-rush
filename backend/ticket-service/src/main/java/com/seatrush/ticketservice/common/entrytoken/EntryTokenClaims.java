package com.seatrush.ticketservice.common.entrytoken;

import java.time.Instant;

public record EntryTokenClaims(
        String jti,
        Long userId,
        Long scheduleId,
        Instant expiresAt
) {
}
