package com.seatrush.queueservice.domain.queue.dto.response;

import com.seatrush.queueservice.domain.queue.QueueStatus;

public record QueueJoinResponseDto(
        Long scheduleId,
        long position,
        long totalWaiting,
        QueueStatus status,
        boolean alreadyJoined
) {
}
