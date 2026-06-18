package com.seatrush.notificationconsumer.domain.notification.repository;

import com.seatrush.notificationconsumer.domain.notification.config.NotificationDeduplicationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Redis를 사용해 알림 이벤트의 중복 발송을 방지합니다.
 *
 * 이벤트 처리 전 PROCESSING 상태로 선점하고, 발송 완료 후 COMPLETED 상태로 변경합니다.
 */
@Repository
public class NotificationDeduplicationRepository {

    private static final String KEY_PREFIX = "notification:event:";
    private static final String COMPLETED = "COMPLETED";

    private static final DefaultRedisScript<Long> CLAIM_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>();

    static {
        CLAIM_SCRIPT.setLocation(new ClassPathResource("scripts/claim_event.lua"));
        CLAIM_SCRIPT.setResultType(Long.class);

        RELEASE_SCRIPT.setLocation(new ClassPathResource("scripts/release_event.lua"));
        RELEASE_SCRIPT.setResultType(Long.class);
    }

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
     * 이벤트 처리 권한을 선점합니다.
     */
    public NotificationClaimResult claim(UUID eventId) {
        Long result = redisTemplate.execute(
                CLAIM_SCRIPT,
                List.of(key(eventId)),
                String.valueOf(properties.processingTtl().toMillis())
        );
        if (result == null) {
            throw new IllegalStateException("알림 이벤트 선점 결과를 확인할 수 없습니다.");
        }
        return switch (result.intValue()) {
            case 1 -> NotificationClaimResult.CLAIMED;
            case 2 -> NotificationClaimResult.COMPLETED;
            default -> NotificationClaimResult.PROCESSING;
        };
    }

    /**
     * 알림 발송이 끝난 이벤트를 완료 상태로 저장합니다.
     */
    public void complete(UUID eventId) {
        redisTemplate.opsForValue().set(
                key(eventId),
                COMPLETED,
                properties.completedTtl()
        );
    }

    /**
     * 알림 발송 실패 시 PROCESSING 선점을 해제해 재시도할 수 있게 합니다.
     */
    public void release(UUID eventId) {
        redisTemplate.execute(RELEASE_SCRIPT, List.of(key(eventId)));
    }

    /**
     * 이벤트 ID 기반 Redis key를 생성합니다.
     */
    private String key(UUID eventId) {
        return KEY_PREFIX + eventId;
    }
}
