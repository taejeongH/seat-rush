package com.seatrush.ticketservice.domain.seat.repository;

public final class SeatHoldKey {

    private static final String SEAT_HOLD_PREFIX = "seat:hold:seat:";
    private static final String SECTION_HOLD_INDEX_PREFIX = "seat:hold:index:";
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

    /**
     * 구역별로 현재 선점된 좌석만 조회하기 위한 Sorted Set key를 생성합니다.
     */
    public static String sectionIndex(Long scheduleId, Long sectionId, String practiceSessionId) {
        return scope(practiceSessionId)
                + SECTION_HOLD_INDEX_PREFIX
                + scheduleId
                + ":"
                + sectionId;
    }

    private static String scope(String practiceSessionId) {
        if (practiceSessionId == null || practiceSessionId.isBlank()) {
            return "";
        }
        return PRACTICE_PREFIX_FORMAT.formatted(practiceSessionId);
    }
}
