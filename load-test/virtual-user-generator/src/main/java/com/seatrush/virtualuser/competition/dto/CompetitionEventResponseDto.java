package com.seatrush.virtualuser.competition.dto;

import java.time.Instant;

public record CompetitionEventResponseDto(
        int userNumber,
        String status,
        String detail,
        Instant occurredAt
) {
}
