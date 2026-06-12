package com.seatrush.queueservice.domain.entrytoken;

import java.time.Instant;

public record EntryTokenCandidate(
        String token,
        String jti,
        Instant expiresAt
) {
}
