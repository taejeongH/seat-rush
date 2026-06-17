package com.seatrush.ticketservice.common.entrytoken;

import java.time.Instant;

public record EntryTokenClaims(
        String jti,
        Long userId,
        Long scheduleId,
        String practiceSessionId,
        Instant expiresAt
) {

    public EntryTokenClaims(
            String jti,
            Long userId,
            Long scheduleId,
            Instant expiresAt
    ) {
        this(jti, userId, scheduleId, null, expiresAt);
    }

    public boolean practiceMode() {
        return practiceSessionId != null && !practiceSessionId.isBlank();
    }
}
