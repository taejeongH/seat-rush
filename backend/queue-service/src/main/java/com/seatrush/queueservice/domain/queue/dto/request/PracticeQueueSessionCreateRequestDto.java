package com.seatrush.queueservice.domain.queue.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

public record PracticeQueueSessionCreateRequestDto(
        @NotNull @Positive Long seatLayoutId,
        @NotBlank String practiceSessionId,
        @NotNull Instant bookingOpenAt,
        @NotNull Instant bookingCloseAt
) {
}
