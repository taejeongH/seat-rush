package com.seatrush.queueservice.domain.entrytoken;

/**
 * entryToken과 사용자별 발급 정보를 저장하는 Redis 키 규칙을 관리합니다.
 */
public final class EntryTokenKey {

    private static final String USER_TOKEN_KEY_FORMAT = "entry-token:schedule:%d:user:%d";

    private EntryTokenKey() {
    }

    public static String userToken(Long scheduleId, Long userId) {
        return USER_TOKEN_KEY_FORMAT.formatted(scheduleId, userId);
    }
}
