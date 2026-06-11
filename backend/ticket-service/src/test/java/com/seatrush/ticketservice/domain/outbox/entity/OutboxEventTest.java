package com.seatrush.ticketservice.domain.outbox.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Outbox 이벤트의 선점, 발행 결과, 재시도 상태 전이를 검증합니다.
 */
class OutboxEventTest {

    /**
     * PENDING 이벤트를 선점하면 PROCESSING 상태와 Worker lease를 기록합니다.
     */
    @Test
    void claimPendingEvent() {
        OutboxEvent event = createEvent();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = now.plusSeconds(30);

        boolean claimed = event.claim("worker-a", now, deadline, 5);

        assertThat(claimed).isTrue();
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
        assertThat(event.getWorkerId()).isEqualTo("worker-a");
        assertThat(event.getProcessingDeadline()).isEqualTo(deadline);
    }

    /**
     * lease가 만료된 이벤트를 재선점하면 실패 횟수를 증가시킵니다.
     */
    @Test
    void reclaimExpiredProcessingEvent() {
        OutboxEvent event = createEvent();
        LocalDateTime now = LocalDateTime.now();
        event.claim("worker-a", now, now.plusSeconds(30), 5);

        boolean claimed = event.claim(
                "worker-b",
                now.plusSeconds(31),
                now.plusSeconds(61),
                5
        );

        assertThat(claimed).isTrue();
        assertThat(event.getWorkerId()).isEqualTo("worker-b");
        assertThat(event.getRetryCount()).isEqualTo(1);
    }

    /**
     * 이벤트를 소유하지 않은 Worker의 처리 결과는 상태에 반영하지 않습니다.
     */
    @Test
    void ignoreResultFromNonOwnerWorker() {
        OutboxEvent event = createEvent();
        LocalDateTime now = LocalDateTime.now();
        event.claim("worker-a", now, now.plusSeconds(30), 5);

        boolean updated = event.markPublished("worker-b", now.plusSeconds(1));

        assertThat(updated).isFalse();
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
    }

    /**
     * 재시도 가능한 실패가 최대 횟수를 넘으면 FAILED 상태로 전환합니다.
     */
    @Test
    void stopRetryingAfterMaxRetryCount() {
        OutboxEvent event = createEvent();
        LocalDateTime now = LocalDateTime.now();

        for (int attempt = 1; attempt <= 6; attempt++) {
            String workerId = "worker-" + attempt;
            event.claim(workerId, now, now.plusSeconds(30), 5);
            event.markRetryableFailure(workerId, "failure", 5, now);
        }

        assertThat(event.getRetryCount()).isEqualTo(6);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
    }

    /**
     * 복구할 수 없는 오류는 재시도 없이 즉시 FAILED 상태로 전환합니다.
     */
    @Test
    void markPermanentFailureImmediately() {
        OutboxEvent event = createEvent();
        LocalDateTime now = LocalDateTime.now();
        event.claim("worker-a", now, now.plusSeconds(30), 5);

        boolean updated = event.markPermanentFailure(
                "worker-a",
                "invalid payload",
                now.plusSeconds(1)
        );

        assertThat(updated).isTrue();
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getWorkerId()).isNull();
    }

    private OutboxEvent createEvent() {
        return OutboxEvent.create(
                UUID.randomUUID(),
                "CONCERT_SCHEDULE",
                1L,
                "CREATED",
                "schedule-status-v1",
                "{}"
        );
    }
}
