package com.seatrush.ticketservice.domain.seat.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record SeatHoldRequestDto(
        @NotEmpty List<@Positive Long> seatIds
) {
}
