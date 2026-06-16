package com.seatrush.virtualuser.competition.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record CompetitionStartRequestDto(
        @NotNull
        @Min(1)
        Long scheduleId,

        @Min(1)
        @Max(10000)
        int virtualUsers,

        @NotNull
        OffsetDateTime startAt,

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
