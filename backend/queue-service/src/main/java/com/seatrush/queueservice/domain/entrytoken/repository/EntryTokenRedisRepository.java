package com.seatrush.queueservice.domain.entrytoken.repository;

import com.seatrush.queueservice.domain.entrytoken.EntryTokenKey;
import com.seatrush.queueservice.domain.queue.QueueKey;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * entryToken Î∞úÍ∏âÍ≥?Í≤ÄÏ¶ùÏùÑ Redis Lua ScriptÎ°??êÏûê?ÅÏúºÎ°?Ï≤òÎ¶¨?©Îãà??
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
     * ?ÖÏû• Í∞Ä???úÎ≤àÍ≥??úÏÑ± ?†ÌÅ∞ ?òÎ? ?ïÏù∏?òÍ≥† entryToken??Î∞úÍ∏â?©Îãà??
     */
    @SuppressWarnings("unchecked")
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
            throw new IllegalStateException("entryToken Î∞úÍ∏â Í≤∞Í≥ºÎ•??ïÏù∏?????ÜÏäµ?àÎã§.");
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
            default -> throw new IllegalStateException("?????ÜÎäî entryToken Î∞úÍ∏â Í≤∞Í≥º?ÖÎãà?? " + resultCode);
        };
    }

    /**
     * entryTokenÍ≥?active entry memberÎ•??úÍ±∞???ÖÏû• ?¨Î°Ø??Î∞òÌôò?©Îãà??
     */
    public boolean releaseSlot(
            Long scheduleId,
            Long userId,
            String entryTokenId
    ) {
        return releaseSlot(scheduleId, userId, entryTokenId, null);
    }

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
