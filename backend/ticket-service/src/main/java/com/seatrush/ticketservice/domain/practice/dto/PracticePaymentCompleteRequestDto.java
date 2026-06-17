package com.seatrush.ticketservice.domain.practice.dto;

import jakarta.validation.constraints.Pattern;

public record PracticePaymentCompleteRequestDto(
        @Pattern(regexp = "SUCCESS|FAILED")
        String result
) {
}
