package com.seatrush.ticketservice.domain.outbox.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatrush.ticketservice.domain.outbox.config.OutboxRelayProperties;
import com.seatrush.ticketservice.domain.outbox.entity.OutboxEvent;
import com.seatrush.ticketservice.domain.outbox.service.OutboxClaimService;
import com.seatrush.ticketservice.domain.outbox.service.OutboxResultService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Outbox Relay의 Kafka 발행 결과 전달을 검증합니다.
 */
class OutboxEventRelayTest {

    private OutboxClaimService outboxClaimService;
    private OutboxResultService outboxResultService;
    private KafkaTemplate<String, Object> kafkaTemplate;
    private OutboxEventRelay outboxEventRelay;

    @BeforeEach
    void setUp() {
        outboxClaimService = mock(OutboxClaimService.class);
        outboxResultService = mock(OutboxResultService.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        outboxEventRelay = new OutboxEventRelay(
                outboxClaimService,
                outboxResultService,
                kafkaTemplate,
                new ObjectMapper(),
                new OutboxRelayProperties(100, 5, 10, 30)
        );
    }

    /**
     * Kafka 발행에 성공하면 결과 저장 서비스에 발행 완료를 요청합니다.
     */
    @Test
    void savePublishedResultWhenKafkaSendSucceeds() {
        OutboxEvent event = createEvent("{}");
        when(outboxClaimService.claimNext(anyString()))
                .thenReturn(Optional.of(event), Optional.empty());
        when(kafkaTemplate.send(anyString(), anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        when(outboxResultService.markPublished(isNull(), anyString())).thenReturn(true);

        outboxEventRelay.publishPendingEvents();

        verify(outboxResultService).markPublished(isNull(), anyString());
    }

    /**
     * Kafka 발행 실패는 재시도 가능한 실패로 저장합니다.
     */
    @Test
    void saveRetryableFailureWhenKafkaSendFails() {
        OutboxEvent event = createEvent("{}");
        CompletableFuture<SendResult<String, Object>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new IllegalStateException("Kafka unavailable"));
        when(outboxClaimService.claimNext(anyString()))
                .thenReturn(Optional.of(event), Optional.empty());
        when(kafkaTemplate.send(anyString(), anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(failedFuture);

        outboxEventRelay.publishPendingEvents();

        verify(outboxResultService).markRetryableFailure(
                isNull(),
                anyString(),
                anyString()
        );
    }

    /**
     * JSON payload가 손상된 이벤트는 재시도 없이 영구 실패로 저장합니다.
     */
    @Test
    void savePermanentFailureForInvalidPayload() {
        OutboxEvent event = createEvent("{invalid-json");
        when(outboxClaimService.claimNext(anyString()))
                .thenReturn(Optional.of(event), Optional.empty());

        outboxEventRelay.publishPendingEvents();

        verify(outboxResultService).markPermanentFailure(
                isNull(),
                anyString(),
                anyString()
        );
    }

    private OutboxEvent createEvent(String payload) {
        return OutboxEvent.create(
                UUID.randomUUID(),
                "CONCERT_SCHEDULE",
                1L,
                "CREATED",
                "schedule-status-v1",
                payload
        );
    }
}
