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
    private static final String PRACTICE_PREFIX_FORMAT = "practice:%s:";
    private static final String PRACTICE_ALL_KEYS_PATTERN_FORMAT = "practice:%s:*";

    private QueueKey() {
    }

    public static String waiting(Long scheduleId) {
        return WAITING_KEY_FORMAT.formatted(scheduleId);
    }

    public static String waiting(Long scheduleId, String practiceSessionId) {
        return scope(practiceSessionId) + waiting(scheduleId);
    }

    public static String sequence(Long scheduleId) {
        return SEQUENCE_KEY_FORMAT.formatted(scheduleId);
    }

    public static String sequence(Long scheduleId, String practiceSessionId) {
        return scope(practiceSessionId) + sequence(scheduleId);
    }

    public static String scheduleState(Long scheduleId) {
        return SCHEDULE_STATE_KEY_FORMAT.formatted(scheduleId);
    }

    public static String scheduleState(Long scheduleId, String practiceSessionId) {
        return scope(practiceSessionId) + scheduleState(scheduleId);
    }

    public static String activeEntries(Long scheduleId) {
        return ACTIVE_ENTRY_KEY_FORMAT.formatted(scheduleId);
    }

    public static String activeEntries(Long scheduleId, String practiceSessionId) {
        return scope(practiceSessionId) + activeEntries(scheduleId);
    }

    public static String session(Long scheduleId, Long userId) {
        return SESSION_KEY_FORMAT.formatted(scheduleId, userId);
    }

    public static String session(Long scheduleId, Long userId, String practiceSessionId) {
        return scope(practiceSessionId) + session(scheduleId, userId);
    }

    public static String sessionExpirations(Long scheduleId) {
        return SESSION_EXPIRATION_KEY_FORMAT.formatted(scheduleId);
    }

    public static String sessionExpirations(Long scheduleId, String practiceSessionId) {
        return scope(practiceSessionId) + sessionExpirations(scheduleId);
    }

    public static String practiceKeys(String practiceSessionId) {
        return PRACTICE_ALL_KEYS_PATTERN_FORMAT.formatted(practiceSessionId);
    }

    private static String scope(String practiceSessionId) {
        if (practiceSessionId == null || practiceSessionId.isBlank()) {
            return "";
        }
        return PRACTICE_PREFIX_FORMAT.formatted(practiceSessionId);
    }
}
