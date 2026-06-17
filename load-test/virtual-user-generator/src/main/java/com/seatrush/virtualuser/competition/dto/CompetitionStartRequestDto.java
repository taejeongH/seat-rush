package com.seatrush.virtualuser.competition.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CompetitionStartRequestDto(
        @NotNull
        @Min(1)
        Long seatLayoutId,

        @NotNull
        String practiceSessionId,

        @Min(1)
        @Max(10000)
        int virtualUsers,

        @Min(0)
        @Max(3600)
        int countdownSeconds,

        @Min(1)
        @Max(180)
        int practiceDurationMinutes,

        @Min(1)
        @Max(500)
        int prepareConcurrency,

        @Min(0)
        @Max(30000)
        long joinJitterMillis,

        @NotNull
        BehaviorWeights behaviors
) {

    public record BehaviorWeights(
            @Min(0) int abandonQueue,
            @Min(0) int abandonAfterEntry,
            @Min(0) int abandonAfterHold,
            @Min(0) int paymentFailure,
            @Min(0) int paymentSuccess
    ) {

        public int total() {
            return abandonQueue
                    + abandonAfterEntry
                    + abandonAfterHold
                    + paymentFailure
                    + paymentSuccess;
        }
    }
}
