package com.seatrush.queueservice.domain.entrytoken.dto.response;

import java.time.Instant;

public record EntryTokenIssueResponseDto(
        Long scheduleId,
        String entryToken,
        Instant expiresAt,
        boolean alreadyIssued
) {
}
