package com.seatrush.ticketservice.domain.seat.repository;

public record SeatHoldResult(
        boolean success,
        Long unavailableSeatId
) {

    public static SeatHoldResult held() {
        return new SeatHoldResult(true, null);
    }

    public static SeatHoldResult unavailable(Long seatId) {
        return new SeatHoldResult(false, seatId);
    }
}
