package com.seatrush.ticketservice.domain.reservation.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ReservationCreateRequestDto(
        @NotBlank String holdId
) {
}
