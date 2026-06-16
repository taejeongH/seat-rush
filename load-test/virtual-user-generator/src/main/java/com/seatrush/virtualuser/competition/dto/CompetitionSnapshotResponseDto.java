package com.seatrush.virtualuser.competition.dto;

import com.seatrush.virtualuser.competition.CompetitionStatus;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record CompetitionSnapshotResponseDto(
        String runId,
        CompetitionStatus status,
        String gatewayBaseUrl,
        Long scheduleId,
        int totalUsers,
        int completedUsers,
        OffsetDateTime startAt,
        Instant updatedAt,
        Map<String, Long> userStatuses,
        List<CompetitionEventResponseDto> recentEvents
) {
}
