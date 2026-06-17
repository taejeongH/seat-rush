package com.seatrush.ticketservice.domain.seat.repository;

public final class SeatHoldKey {

    private static final String SEAT_HOLD_PREFIX = "seat:hold:seat:";
    private static final String HOLD_PREFIX = "seat:hold:";
    private static final String PRACTICE_PREFIX_FORMAT = "practice:%s:";

    private SeatHoldKey() {
    }

    public static String seat(Long scheduleId, Long seatId) {
        return SEAT_HOLD_PREFIX + scheduleId + ":" + seatId;
    }

    public static String seat(Long scheduleId, Long seatId, String practiceSessionId) {
        return scope(practiceSessionId) + seat(scheduleId, seatId);
    }

    public static String hold(String holdId) {
        return HOLD_PREFIX + holdId;
    }

    public static String hold(String holdId, String practiceSessionId) {
        return scope(practiceSessionId) + hold(holdId);
    }

    private static String scope(String practiceSessionId) {
        if (practiceSessionId == null || practiceSessionId.isBlank()) {
            return "";
        }
        return PRACTICE_PREFIX_FORMAT.formatted(practiceSessionId);
    }
}
