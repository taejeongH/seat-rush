package com.seatrush.ticketservice.domain.seat.dto.response;

import com.seatrush.ticketservice.domain.seat.repository.SeatHold;

import java.time.Instant;
import java.util.List;

public record SeatHoldResponseDto(
        String holdId,
        Long scheduleId,
        List<Long> seatIds,
        Instant expiresAt
) {

    public static SeatHoldResponseDto from(SeatHold hold) {
        return new SeatHoldResponseDto(
                hold.holdId(),
                hold.scheduleId(),
                hold.seatIds(),
                hold.expiresAt()
        );
    }
}
