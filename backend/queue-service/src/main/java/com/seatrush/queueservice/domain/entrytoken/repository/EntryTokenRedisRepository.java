package com.seatrush.queueservice.domain.entrytoken.repository;

import com.seatrush.queueservice.domain.entrytoken.EntryTokenKey;
import com.seatrush.queueservice.domain.queue.QueueKey;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * entryToken 발급과 검증을 Redis Lua Script로 원자적으로 처리합니다.
 */
@Repository
public class EntryTokenRedisRepository {

    private static final DefaultRedisScript<List> ISSUE_ENTRY_TOKEN_SCRIPT =
            new DefaultRedisScript<>("""
                    local redisTime = redis.call('TIME')
                    local nowMillis =
                        tonumber(redisTime[1]) * 1000
                        + math.floor(tonumber(redisTime[2]) / 1000)

                    local expiredUsers = redis.call('ZRANGEBYSCORE', KEYS[6], '-inf', nowMillis)
                    for _, expiredUserId in ipairs(expiredUsers) do
                        redis.call('ZREM', KEYS[1], expiredUserId)
                        redis.call('ZREM', KEYS[6], expiredUserId)
                    end

                    redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', nowMillis)

                    local existingToken = redis.call('GET', KEYS[3])
                    if existingToken then
                        local remainingTtl = redis.call('PTTL', KEYS[3])
                        if remainingTtl > 0 then
                            return {1, existingToken, nowMillis + remainingTtl}
                        end
                        redis.call('DEL', KEYS[3])
                    end

                    local schedule = redis.call(
                        'HMGET',
                        KEYS[4],
                        'status',
                        'bookingOpenAt',
                        'bookingCloseAt'
                    )
                    local scheduleStatus = schedule[1]
                    local bookingOpenAt = tonumber(schedule[2])
                    local bookingCloseAt = tonumber(schedule[3])

                    if not scheduleStatus or not bookingOpenAt or not bookingCloseAt then
                        return {-3, '', 0}
                    end

                    if scheduleStatus == 'CANCELED'
                        or scheduleStatus == 'BOOKING_CLOSED'
                        or nowMillis < bookingOpenAt
                        or nowMillis >= bookingCloseAt then
                        return {-4, '', 0}
                    end

                    local rank = redis.call('ZRANK', KEYS[1], ARGV[1])
                    if not rank then
                        return {-1, '', 0}
                    end

                    local activeCount = redis.call('ZCARD', KEYS[2])
                    local availableSlots = tonumber(ARGV[2]) - activeCount
                    if availableSlots <= 0 or rank >= availableSlots then
                        return {-2, '', 0}
                    end

                    local expiresAt = nowMillis + tonumber(ARGV[4])
                    redis.call('ZREM', KEYS[1], ARGV[1])
                    redis.call('DEL', KEYS[5])
                    redis.call('ZREM', KEYS[6], ARGV[1])
                    redis.call('PSETEX', KEYS[3], ARGV[4], ARGV[3])
                    redis.call('ZADD', KEYS[2], expiresAt, ARGV[5])

                    return {0, ARGV[3], expiresAt}
                    """, List.class);

    private static final DefaultRedisScript<Long> RELEASE_ENTRY_SLOT_SCRIPT =
            new DefaultRedisScript<>("""
                    redis.call('DEL', KEYS[1])
                    return redis.call('ZREM', KEYS[2], ARGV[1])
                    """, Long.class);

    private final RedisTemplate<String, String> redisTemplate;

    public EntryTokenRedisRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 입장 가능 순번과 활성 토큰 수를 확인하고 entryToken을 발급합니다.
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
        List<Object> result = redisTemplate.execute(
                ISSUE_ENTRY_TOKEN_SCRIPT,
                List.of(
                        QueueKey.waiting(scheduleId),
                        QueueKey.activeEntries(scheduleId),
                        EntryTokenKey.userToken(scheduleId, userId),
                        QueueKey.scheduleState(scheduleId),
                        QueueKey.session(scheduleId, userId),
                        QueueKey.sessionExpirations(scheduleId)
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
            default -> throw new IllegalStateException("알 수 없는 entryToken 발급 결과입니다: " + resultCode);
        };
    }

    /**
     * entryToken과 active entry member를 제거해 입장 슬롯을 반환합니다.
     */
    public boolean releaseSlot(
            Long scheduleId,
            Long userId,
            String entryTokenId
    ) {
        Long removed = redisTemplate.execute(
                RELEASE_ENTRY_SLOT_SCRIPT,
                List.of(
                        EntryTokenKey.userToken(scheduleId, userId),
                        QueueKey.activeEntries(scheduleId)
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
