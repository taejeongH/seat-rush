package com.seatrush.queueservice.domain.schedule.repository;

import com.seatrush.queueservice.domain.queue.QueueKey;
import com.seatrush.queueservice.domain.schedule.config.ScheduleTimeProperties;
import com.seatrush.queueservice.domain.schedule.event.ScheduleStatusEvent;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Queue Service에 필요한 최소 회차 상태를 Redis에 동기화합니다.
 */
@Repository
public class ScheduleStateRedisRepository {

    private static final DefaultRedisScript<Long> SYNC_SCHEDULE_SCRIPT =
            new DefaultRedisScript<>("""
                    local current = redis.call(
                        'HMGET',
                        KEYS[1],
                        'version',
                        'bookingOpenAt',
                        'bookingCloseAt'
                    )
                    local currentVersion = current[1]

                    if currentVersion then
                        if tonumber(currentVersion) > tonumber(ARGV[4]) then
                            return 0
                        end

                        local timeFormatIsCurrent =
                            tonumber(current[2]) ~= nil and tonumber(current[3]) ~= nil
                        if tonumber(currentVersion) == tonumber(ARGV[4])
                            and timeFormatIsCurrent then
                            return 0
                        end
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
    private final ScheduleTimeProperties properties;

    public ScheduleStateRedisRepository(
            RedisTemplate<String, String> redisTemplate,
            ScheduleTimeProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /**
     * 현재 version보다 새로운 이벤트만 반영하며 기존 문자열 시간 형식은 다시 저장합니다.
     */
    public ScheduleStateSyncResult synchronize(ScheduleStatusEvent event) {
        Long result = redisTemplate.execute(
                SYNC_SCHEDULE_SCRIPT,
                List.of(QueueKey.scheduleState(event.scheduleId())),
                event.status().name(),
                toEpochMillis(event.bookingOpenAt()),
                toEpochMillis(event.bookingCloseAt()),
                Long.toString(event.version())
        );

        if (result == null) {
            throw new IllegalStateException("회차 상태 동기화 결과를 확인할 수 없습니다.");
        }

        return result == 1
                ? ScheduleStateSyncResult.APPLIED
                : ScheduleStateSyncResult.IGNORED;
    }

    private String toEpochMillis(LocalDateTime dateTime) {
        return Long.toString(
                dateTime.atZone(properties.zoneId())
                        .toInstant()
                        .toEpochMilli()
        );
    }
}
