package com.seatrush.ticketservice.domain.seat.repository;

public final class SeatHoldKey {

    private static final String SEAT_HOLD_PREFIX = "seat:hold:seat:";
    private static final String HOLD_PREFIX = "seat:hold:";

    private SeatHoldKey() {
    }

    public static String seat(Long scheduleId, Long seatId) {
        return SEAT_HOLD_PREFIX + scheduleId + ":" + seatId;
    }

    public static String hold(String holdId) {
        return HOLD_PREFIX + holdId;
    }
}
