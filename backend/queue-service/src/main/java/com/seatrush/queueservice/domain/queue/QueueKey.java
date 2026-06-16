package com.seatrush.queueservice.domain.queue;

/**
 * 회차별 대기열 Redis 키 규칙을 관리합니다.
 */
public final class QueueKey {

    private static final String WAITING_KEY_FORMAT = "queue:schedule:%d:waiting";
    private static final String SEQUENCE_KEY_FORMAT = "queue:schedule:%d:sequence";
    private static final String SCHEDULE_STATE_KEY_FORMAT = "queue:schedule:%d:state";
    private static final String ACTIVE_ENTRY_KEY_FORMAT = "queue:schedule:%d:active-entries";
    private static final String SESSION_KEY_FORMAT = "queue:schedule:%d:user:%d:session";
    private static final String SESSION_EXPIRATION_KEY_FORMAT = "queue:schedule:%d:session-expirations";

    private QueueKey() {
    }

    public static String waiting(Long scheduleId) {
        return WAITING_KEY_FORMAT.formatted(scheduleId);
    }

    public static String sequence(Long scheduleId) {
        return SEQUENCE_KEY_FORMAT.formatted(scheduleId);
    }

    public static String scheduleState(Long scheduleId) {
        return SCHEDULE_STATE_KEY_FORMAT.formatted(scheduleId);
    }

    public static String activeEntries(Long scheduleId) {
        return ACTIVE_ENTRY_KEY_FORMAT.formatted(scheduleId);
    }

    public static String session(Long scheduleId, Long userId) {
        return SESSION_KEY_FORMAT.formatted(scheduleId, userId);
    }

    public static String sessionExpirations(Long scheduleId) {
        return SESSION_EXPIRATION_KEY_FORMAT.formatted(scheduleId);
    }
}
