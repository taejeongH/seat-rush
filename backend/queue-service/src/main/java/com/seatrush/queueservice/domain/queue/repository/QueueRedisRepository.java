package com.seatrush.queueservice.domain.queue.repository;

import com.seatrush.queueservice.domain.queue.QueueKey;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.Set;

/**
 * Redis Sorted Set을 사용해 회차별 대기열을 저장하고 조회합니다.
 */
@Repository
public class QueueRedisRepository {

    private static final DefaultRedisScript<List> GET_ADMISSION_STATE_SCRIPT =
            new DefaultRedisScript<>("""
                    local redisTime = redis.call('TIME')
                    local nowMillis =
                        tonumber(redisTime[1]) * 1000
                        + math.floor(tonumber(redisTime[2]) / 1000)

                    local expiredUsers = redis.call('ZRANGEBYSCORE', KEYS[3], '-inf', nowMillis)
                    for _, expiredUserId in ipairs(expiredUsers) do
                        redis.call('ZREM', KEYS[1], expiredUserId)
                        redis.call('ZREM', KEYS[3], expiredUserId)
                    end

                    redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', nowMillis)

                    local rank = redis.call('ZRANK', KEYS[1], ARGV[1])
                    if not rank then
                        return {-1, 0, 0}
                    end

                    local sessionExpiresAt = nowMillis + tonumber(ARGV[3])
                    redis.call('PSETEX', KEYS[4], ARGV[3], '1')
                    redis.call('ZADD', KEYS[3], sessionExpiresAt, ARGV[1])

                    local totalWaiting = redis.call('ZCARD', KEYS[1])
                    local activeCount = redis.call('ZCARD', KEYS[2])
                    local availableSlots = tonumber(ARGV[2]) - activeCount
                    local enterable = 0

                    if availableSlots > 0 and rank < availableSlots then
                        enterable = 1
                    end

                    return {rank + 1, totalWaiting, enterable}
                    """, List.class);

    private static final DefaultRedisScript<List> JOIN_QUEUE_SCRIPT =
            new DefaultRedisScript<>("""
                    local redisTime = redis.call('TIME')
                    local nowMillis =
                        tonumber(redisTime[1]) * 1000
                        + math.floor(tonumber(redisTime[2]) / 1000)

                    local expiredUsers = redis.call('ZRANGEBYSCORE', KEYS[4], '-inf', nowMillis)
                    for _, expiredUserId in ipairs(expiredUsers) do
                        redis.call('ZREM', KEYS[1], expiredUserId)
                        redis.call('ZREM', KEYS[4], expiredUserId)
                    end

                    local schedule = redis.call(
                        'HMGET',
                        KEYS[3],
                        'status',
                        'bookingOpenAt',
                        'bookingCloseAt'
                    )
                    local scheduleStatus = schedule[1]
                    local bookingOpenAt = tonumber(schedule[2])
                    local bookingCloseAt = tonumber(schedule[3])

                    if not scheduleStatus or not bookingOpenAt or not bookingCloseAt then
                        return {-1, 0}
                    end

                    if scheduleStatus == 'CANCELED'
                        or scheduleStatus == 'BOOKING_CLOSED' then
                        return {-2, 0}
                    end

                    if nowMillis < bookingOpenAt or nowMillis >= bookingCloseAt then
                        return {-2, 0}
                    end

                    local sessionExpiresAt = nowMillis + tonumber(ARGV[2])
                    if redis.call('ZSCORE', KEYS[1], ARGV[1]) then
                        redis.call('PSETEX', KEYS[5], ARGV[2], '1')
                        redis.call('ZADD', KEYS[4], sessionExpiresAt, ARGV[1])
                        local rank = redis.call('ZRANK', KEYS[1], ARGV[1])
                        return {rank + 1, 1}
                    end

                    local sequence = redis.call('INCR', KEYS[2])
                    redis.call('ZADD', KEYS[1], sequence, ARGV[1])
                    redis.call('PSETEX', KEYS[5], ARGV[2], '1')
                    redis.call('ZADD', KEYS[4], sessionExpiresAt, ARGV[1])
                    local rank = redis.call('ZRANK', KEYS[1], ARGV[1])
                    return {rank + 1, 0}
                    """, List.class);

    private static final DefaultRedisScript<Long> HEARTBEAT_SCRIPT =
            new DefaultRedisScript<>("""
                    local redisTime = redis.call('TIME')
                    local nowMillis =
                        tonumber(redisTime[1]) * 1000
                        + math.floor(tonumber(redisTime[2]) / 1000)

                    local expiredUsers = redis.call('ZRANGEBYSCORE', KEYS[2], '-inf', nowMillis)
                    for _, expiredUserId in ipairs(expiredUsers) do
                        redis.call('ZREM', KEYS[1], expiredUserId)
                        redis.call('ZREM', KEYS[2], expiredUserId)
                    end

                    local rank = redis.call('ZRANK', KEYS[1], ARGV[1])
                    if not rank then
                        return 0
                    end

                    local sessionExpiresAt = nowMillis + tonumber(ARGV[2])
                    redis.call('PSETEX', KEYS[3], ARGV[2], '1')
                    redis.call('ZADD', KEYS[2], sessionExpiresAt, ARGV[1])
                    return 1
                    """, Long.class);

    private final RedisTemplate<String, String> redisTemplate;

    public QueueRedisRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 오픈 시간 판정, 중복 확인, 순번 생성, 대기열 등록을 하나의 Lua Script로 처리합니다.
     *
     * @return 현재 대기 순번과 기존 진입 여부
     */
    @SuppressWarnings("unchecked")
    public QueueJoinResult join(Long scheduleId, Long userId, long sessionTtlMillis) {
        return join(scheduleId, userId, sessionTtlMillis, null);
    }

    public QueueJoinResult join(
            Long scheduleId,
            Long userId,
            long sessionTtlMillis,
            String practiceSessionId
    ) {
        List<Long> result = redisTemplate.execute(
                JOIN_QUEUE_SCRIPT,
                List.of(
                        QueueKey.waiting(scheduleId, practiceSessionId),
                        QueueKey.sequence(scheduleId, practiceSessionId),
                        QueueKey.scheduleState(scheduleId, practiceSessionId),
                        QueueKey.sessionExpirations(scheduleId, practiceSessionId),
                        QueueKey.session(scheduleId, userId, practiceSessionId)
                ),
                userId.toString(),
                Long.toString(sessionTtlMillis)
        );

        if (result == null || result.size() != 2) {
            throw new IllegalStateException("대기열 진입 결과를 확인할 수 없습니다.");
        }

        return new QueueJoinResult(
                result.get(0),
                result.get(1) == 1
        );
    }

    /**
     * Redis의 0부터 시작하는 대기열 rank를 조회합니다.
     */
    public Long getRank(Long scheduleId, Long userId) {
        return redisTemplate.opsForZSet()
                .rank(QueueKey.waiting(scheduleId), userId.toString());
    }

    /**
     * 회차 대기열에 등록된 전체 사용자 수를 조회합니다.
     */
    public long getWaitingCount(Long scheduleId) {
        Long count = redisTemplate.opsForZSet().size(QueueKey.waiting(scheduleId));
        return count == null ? 0 : count;
    }

    public long getWaitingCount(Long scheduleId, String practiceSessionId) {
        Long count = redisTemplate.opsForZSet().size(QueueKey.waiting(scheduleId, practiceSessionId));
        return count == null ? 0 : count;
    }

    /**
     * 현재 순번과 활성 입장 슬롯을 기준으로 사용자의 입장 가능 상태를 조회합니다.
     */
    @SuppressWarnings("unchecked")
    public QueueAdmissionState getAdmissionState(
            Long scheduleId,
            Long userId,
            int admissionCapacity,
            long sessionTtlMillis
    ) {
        return getAdmissionState(scheduleId, userId, admissionCapacity, sessionTtlMillis, null);
    }

    public QueueAdmissionState getAdmissionState(
            Long scheduleId,
            Long userId,
            int admissionCapacity,
            long sessionTtlMillis,
            String practiceSessionId
    ) {
        List<Object> result = redisTemplate.execute(
                GET_ADMISSION_STATE_SCRIPT,
                List.of(
                        QueueKey.waiting(scheduleId, practiceSessionId),
                        QueueKey.activeEntries(scheduleId, practiceSessionId),
                        QueueKey.sessionExpirations(scheduleId, practiceSessionId),
                        QueueKey.session(scheduleId, userId, practiceSessionId)
                ),
                userId.toString(),
                Integer.toString(admissionCapacity),
                Long.toString(sessionTtlMillis)
        );

        if (result == null || result.size() != 3) {
            throw new IllegalStateException("대기열 입장 가능 상태를 확인할 수 없습니다.");
        }

        return new QueueAdmissionState(
                toLong(result.get(0)),
                toLong(result.get(1)),
                toLong(result.get(2)) == 1
        );
    }

    public QueueAdmissionState getAdmissionState(
            Long scheduleId,
            Long userId,
            int admissionCapacity
    ) {
        return getAdmissionState(scheduleId, userId, admissionCapacity, 30_000);
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    public boolean heartbeat(Long scheduleId, Long userId, long sessionTtlMillis) {
        return heartbeat(scheduleId, userId, sessionTtlMillis, null);
    }

    public boolean heartbeat(
            Long scheduleId,
            Long userId,
            long sessionTtlMillis,
            String practiceSessionId
    ) {
        Long result = redisTemplate.execute(
                HEARTBEAT_SCRIPT,
                List.of(
                        QueueKey.waiting(scheduleId, practiceSessionId),
                        QueueKey.sessionExpirations(scheduleId, practiceSessionId),
                        QueueKey.session(scheduleId, userId, practiceSessionId)
                ),
                userId.toString(),
                Long.toString(sessionTtlMillis)
        );
        return result != null && result == 1L;
    }

    public void deletePracticeSession(String practiceSessionId) {
        Set<String> keys = redisTemplate.keys(QueueKey.practiceKeys(practiceSessionId));
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    public void expirePracticeSessionKeys(
            Long scheduleId,
            String practiceSessionId,
            Duration ttl
    ) {
        if (practiceSessionId == null || practiceSessionId.isBlank()) {
            return;
        }

        List.of(
                QueueKey.waiting(scheduleId, practiceSessionId),
                QueueKey.sequence(scheduleId, practiceSessionId),
                QueueKey.scheduleState(scheduleId, practiceSessionId),
                QueueKey.activeEntries(scheduleId, practiceSessionId),
                QueueKey.sessionExpirations(scheduleId, practiceSessionId)
        ).forEach(key -> redisTemplate.expire(key, ttl));
    }

    public void createPracticeSession(
            Long scheduleId,
            String practiceSessionId,
            Instant bookingOpenAt,
            Instant bookingCloseAt
    ) {
        redisTemplate.opsForHash().putAll(
                QueueKey.scheduleState(scheduleId, practiceSessionId),
                Map.of(
                        "status", "BOOKING_OPEN",
                        "bookingOpenAt", Long.toString(bookingOpenAt.toEpochMilli()),
                        "bookingCloseAt", Long.toString(bookingCloseAt.toEpochMilli()),
                        "version", "0"
                )
        );
    }

    public record QueueJoinResult(
            long position,
            boolean alreadyJoined
    ) {
    }
}
