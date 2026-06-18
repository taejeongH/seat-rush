package com.seatrush.queueservice.domain.entrytoken.repository;

import com.seatrush.queueservice.domain.entrytoken.EntryTokenKey;
import com.seatrush.queueservice.domain.queue.QueueKey;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * entryToken 발급 상태와 활성 입장 슬롯을 Redis에 저장하고 조회합니다.
 */
@Repository
public class EntryTokenRedisRepository {

    private static final DefaultRedisScript<List> ISSUE_ENTRY_TOKEN_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<Long> RELEASE_ENTRY_SLOT_SCRIPT = new DefaultRedisScript<>();

    static {
        ISSUE_ENTRY_TOKEN_SCRIPT.setLocation(new ClassPathResource("scripts/issue_entry_token.lua"));
        ISSUE_ENTRY_TOKEN_SCRIPT.setResultType(List.class);

        RELEASE_ENTRY_SLOT_SCRIPT.setLocation(new ClassPathResource("scripts/release_entry_slot.lua"));
        RELEASE_ENTRY_SLOT_SCRIPT.setResultType(Long.class);
    }

    private final RedisTemplate<String, String> redisTemplate;

    public EntryTokenRedisRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 실제 회차 대기열에서 입장 가능한 사용자에게 entryToken을 발급합니다.
     */
    public EntryTokenIssueResult issue(
            Long scheduleId,
            Long userId,
            String entryToken,
            String jti,
            int admissionCapacity,
            long ttlMillis
    ) {
        return issue(scheduleId, userId, entryToken, jti, admissionCapacity, ttlMillis, null);
    }

    /**
     * 지정된 회차 또는 연습 세션에서 입장 가능 여부 확인과 토큰 저장을 원자적으로 처리합니다.
     */
    public EntryTokenIssueResult issue(
            Long scheduleId,
            Long userId,
            String entryToken,
            String jti,
            int admissionCapacity,
            long ttlMillis,
            String practiceSessionId
    ) {
        List<Object> result = redisTemplate.execute(
                ISSUE_ENTRY_TOKEN_SCRIPT,
                List.of(
                        QueueKey.waiting(scheduleId, practiceSessionId),
                        QueueKey.activeEntries(scheduleId, practiceSessionId),
                        EntryTokenKey.userToken(scheduleId, userId, practiceSessionId),
                        QueueKey.scheduleState(scheduleId, practiceSessionId),
                        QueueKey.session(scheduleId, userId, practiceSessionId),
                        QueueKey.sessionExpirations(scheduleId, practiceSessionId)
                ),
                userId.toString(),
                Integer.toString(admissionCapacity),
                entryToken,
                Long.toString(ttlMillis),
                jti
        );

        if (result == null || result.size() != 3) {
            throw new IllegalStateException("entryToken 발급 결과를 확인할 수 없습니다.");
        }

        long resultCode = toLong(result.get(0));
        String issuedToken = result.get(1).toString();
        long expiresAt = toLong(result.get(2));

        return switch ((int) resultCode) {
            case 0 -> new EntryTokenIssueResult(EntryTokenIssueStatus.ISSUED, issuedToken, expiresAt);
            case 1 -> new EntryTokenIssueResult(EntryTokenIssueStatus.ALREADY_ISSUED, issuedToken, expiresAt);
            case -1 -> new EntryTokenIssueResult(EntryTokenIssueStatus.QUEUE_ENTRY_NOT_FOUND, null, 0);
            case -2 -> new EntryTokenIssueResult(EntryTokenIssueStatus.ENTRY_NOT_ALLOWED, null, 0);
            case -3 -> new EntryTokenIssueResult(EntryTokenIssueStatus.SCHEDULE_NOT_FOUND, null, 0);
            case -4 -> new EntryTokenIssueResult(EntryTokenIssueStatus.QUEUE_NOT_OPEN, null, 0);
            default -> throw new IllegalStateException("알 수 없는 entryToken 발급 결과입니다. resultCode=" + resultCode);
        };
    }

    /**
     * 실제 회차의 활성 입장 슬롯을 반환합니다.
     */
    public boolean releaseSlot(
            Long scheduleId,
            Long userId,
            String entryTokenId
    ) {
        return releaseSlot(scheduleId, userId, entryTokenId, null);
    }

    /**
     * 지정된 회차 또는 연습 세션의 활성 입장 슬롯을 반환합니다.
     */
    public boolean releaseSlot(
            Long scheduleId,
            Long userId,
            String entryTokenId,
            String practiceSessionId
    ) {
        Long removed = redisTemplate.execute(
                RELEASE_ENTRY_SLOT_SCRIPT,
                List.of(
                        EntryTokenKey.userToken(scheduleId, userId, practiceSessionId),
                        QueueKey.activeEntries(scheduleId, practiceSessionId)
                ),
                entryTokenId
        );
        return removed != null && removed > 0;
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }
}
