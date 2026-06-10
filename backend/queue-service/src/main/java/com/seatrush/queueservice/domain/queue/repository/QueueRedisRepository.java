package com.seatrush.queueservice.domain.queue.repository;

import com.seatrush.queueservice.domain.queue.QueueKey;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Redis Sorted Set을 사용해 회차별 대기열을 저장하고 조회합니다.
 */
@Repository
public class QueueRedisRepository {

    private static final DefaultRedisScript<List> JOIN_QUEUE_SCRIPT =
            new DefaultRedisScript<>("""
                    if redis.call('ZSCORE', KEYS[1], ARGV[1]) then
                        local rank = redis.call('ZRANK', KEYS[1], ARGV[1])
                        return {rank + 1, 1}
                    end

                    local sequence = redis.call('INCR', KEYS[2])
                    redis.call('ZADD', KEYS[1], sequence, ARGV[1])
                    local rank = redis.call('ZRANK', KEYS[1], ARGV[1])
                    return {rank + 1, 0}
                    """, List.class);

    private final RedisTemplate<String, String> redisTemplate;

    public QueueRedisRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 중복 확인, 순번 생성, 대기열 등록을 하나의 Lua 스크립트로 원자적으로 처리합니다.
     *
     * @return 현재 대기 순번과 기존 진입 여부
     */
    @SuppressWarnings("unchecked")
    public QueueJoinResult join(Long scheduleId, Long userId) {
        List<Long> result = redisTemplate.execute(
                JOIN_QUEUE_SCRIPT,
                List.of(QueueKey.waiting(scheduleId), QueueKey.sequence(scheduleId)),
                userId.toString()
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
     * Redis의 0부터 시작하는 rank를 조회합니다.
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

    public record QueueJoinResult(
            long position,
            boolean alreadyJoined
    ) {
    }
}
