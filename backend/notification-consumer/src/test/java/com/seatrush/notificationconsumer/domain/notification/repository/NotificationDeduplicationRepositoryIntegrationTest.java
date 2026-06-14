package com.seatrush.notificationconsumer.domain.notification.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis에서 알림 이벤트 처리 상태가 원자적으로 전환되는지 검증합니다.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6381"
})
class NotificationDeduplicationRepositoryIntegrationTest {

    @Autowired
    private NotificationDeduplicationRepository repository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private UUID eventId;

    @AfterEach
    void tearDown() {
        if (eventId != null) {
            redisTemplate.delete("notification:event:" + eventId);
        }
    }

    /**
     * 최초 처리만 권한을 획득하고 처리 중인 중복 요청을 구분합니다.
     */
    @Test
    void claimEventOnlyOnceWhileProcessing() {
        eventId = UUID.randomUUID();

        assertThat(repository.claim(eventId))
                .isEqualTo(NotificationClaimResult.CLAIMED);
        assertThat(repository.claim(eventId))
                .isEqualTo(NotificationClaimResult.PROCESSING);
    }

    /**
     * 완료된 이벤트는 보존 기간 동안 중복 이벤트로 판별합니다.
     */
    @Test
    void identifyCompletedDuplicateEvent() {
        eventId = UUID.randomUUID();
        repository.claim(eventId);
        repository.complete(eventId);

        assertThat(repository.claim(eventId))
                .isEqualTo(NotificationClaimResult.COMPLETED);
    }

    /**
     * 실패한 처리 권한을 해제하면 다음 재시도가 다시 획득할 수 있습니다.
     */
    @Test
    void reclaimEventAfterFailureRelease() {
        eventId = UUID.randomUUID();
        repository.claim(eventId);
        repository.release(eventId);

        assertThat(repository.claim(eventId))
                .isEqualTo(NotificationClaimResult.CLAIMED);
    }
}
