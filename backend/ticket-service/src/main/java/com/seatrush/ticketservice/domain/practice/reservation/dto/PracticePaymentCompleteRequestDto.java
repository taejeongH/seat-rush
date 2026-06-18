package com.seatrush.ticketservice.domain.practice.reservation.dto;

import jakarta.validation.constraints.NotBlank;

public record PracticePaymentCompleteRequestDto(
        @NotBlank String result
) {
}
