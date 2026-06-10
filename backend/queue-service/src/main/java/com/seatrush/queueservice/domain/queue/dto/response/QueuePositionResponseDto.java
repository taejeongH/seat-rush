package com.seatrush.queueservice.domain.queue.dto.response;

import com.seatrush.queueservice.domain.queue.QueueStatus;

public record QueuePositionResponseDto(
        Long scheduleId,
        long position,
        long totalWaiting,
        QueueStatus status
) {
}
