package com.seatrush.queueservice.domain.schedule.repository;

import com.seatrush.queueservice.domain.queue.QueueKey;
import com.seatrush.queueservice.domain.schedule.event.ScheduleStatusEvent;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Queue Service에 필요한 최소 회차 상태를 Redis에 동기화합니다.
 */
@Repository
public class ScheduleStateRedisRepository {

    private static final DefaultRedisScript<Long> SYNC_SCHEDULE_SCRIPT =
            new DefaultRedisScript<>("""
                    local currentVersion = redis.call('HGET', KEYS[1], 'version')

                    if currentVersion and tonumber(currentVersion) >= tonumber(ARGV[4]) then
                        return 0
                    end

                    redis.call(
                        'HSET',
                        KEYS[1],
                        'status', ARGV[1],
                        'bookingOpenAt', ARGV[2],
                        'bookingCloseAt', ARGV[3],
                        'version', ARGV[4]
                    )
                    return 1
                    """, Long.class);

    private final RedisTemplate<String, String> redisTemplate;

    public ScheduleStateRedisRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 현재 version보다 새로운 이벤트만 Redis에 반영합니다.
     */
    public ScheduleStateSyncResult synchronize(ScheduleStatusEvent event) {
        Long result = redisTemplate.execute(
                SYNC_SCHEDULE_SCRIPT,
                List.of(QueueKey.scheduleState(event.scheduleId())),
                event.status().name(),
                event.bookingOpenAt().toString(),
                event.bookingCloseAt().toString(),
                Long.toString(event.version())
        );

        if (result == null) {
            throw new IllegalStateException("회차 상태 동기화 결과를 확인할 수 없습니다.");
        }

        return result == 1
                ? ScheduleStateSyncResult.APPLIED
                : ScheduleStateSyncResult.IGNORED;
    }
}
