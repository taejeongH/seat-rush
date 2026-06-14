package com.seatrush.notificationconsumer.domain.notification.repository;

import com.seatrush.notificationconsumer.domain.notification.config.NotificationDeduplicationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class NotificationDeduplicationRepository {

    private static final String KEY_PREFIX = "notification:event:";
    private static final String PROCESSING = "PROCESSING";
    private static final String COMPLETED = "COMPLETED";

    private static final DefaultRedisScript<Long> CLAIM_SCRIPT =
            new DefaultRedisScript<>("""
                    local status = redis.call('GET', KEYS[1])
                    if status == 'COMPLETED' then
                        return 2
                    end
                    if status == 'PROCESSING' then
                        return 0
                    end
                    redis.call('SET', KEYS[1], 'PROCESSING', 'PX', ARGV[1])
                    return 1
                    """, Long.class);

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
            new DefaultRedisScript<>("""
                    if redis.call('GET', KEYS[1]) == 'PROCESSING' then
                        return redis.call('DEL', KEYS[1])
                    end
                    return 0
                    """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final NotificationDeduplicationProperties properties;

    public NotificationDeduplicationRepository(
            StringRedisTemplate redisTemplate,
            NotificationDeduplicationProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /**
     * 이벤트 처리 권한을 원자적으로 획득하고 현재 중복 처리 상태를 반환합니다.
     */
    public NotificationClaimResult claim(UUID eventId) {
        Long result = redisTemplate.execute(
                CLAIM_SCRIPT,
                List.of(key(eventId)),
                String.valueOf(properties.processingTtl().toMillis())
        );
        if (result == null) {
            throw new IllegalStateException("알림 이벤트 처리 상태를 확인할 수 없습니다.");
        }
        return switch (result.intValue()) {
            case 1 -> NotificationClaimResult.CLAIMED;
            case 2 -> NotificationClaimResult.COMPLETED;
            default -> NotificationClaimResult.PROCESSING;
        };
    }

    /**
     * 발송이 완료된 이벤트를 보존 기간 동안 완료 상태로 기록합니다.
     */
    public void complete(UUID eventId) {
        redisTemplate.opsForValue().set(
                key(eventId),
                COMPLETED,
                properties.completedTtl()
        );
    }

    /**
     * 발송 실패 시 현재 처리 권한을 해제해 Kafka 재시도가 다시 발송할 수 있게 합니다.
     */
    public void release(UUID eventId) {
        redisTemplate.execute(RELEASE_SCRIPT, List.of(key(eventId)));
    }

    private String key(UUID eventId) {
        return KEY_PREFIX + eventId;
    }
}
