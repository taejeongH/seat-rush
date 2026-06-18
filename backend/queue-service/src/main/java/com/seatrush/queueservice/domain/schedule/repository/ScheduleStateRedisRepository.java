package com.seatrush.queueservice.domain.schedule.repository;

import com.seatrush.queueservice.domain.queue.QueueKey;
import com.seatrush.queueservice.domain.schedule.config.ScheduleTimeProperties;
import com.seatrush.queueservice.domain.schedule.event.ScheduleStatusEvent;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Ticket Service에서 전달받은 회차 상태를 Redis에 동기화합니다.
 *
 * version이 더 최신인 이벤트만 반영해 Kafka 재전달이나 순서 역전으로 오래된 상태가 덮어쓰이지 않게 합니다.
 */
@Repository
public class ScheduleStateRedisRepository {

    private static final DefaultRedisScript<Long> SYNC_SCHEDULE_SCRIPT = new DefaultRedisScript<>();

    static {
        SYNC_SCHEDULE_SCRIPT.setLocation(new ClassPathResource("scripts/sync_schedule.lua"));
        SYNC_SCHEDULE_SCRIPT.setResultType(Long.class);
    }

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
     * 회차 상태 이벤트를 Redis Hash에 반영합니다.
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
