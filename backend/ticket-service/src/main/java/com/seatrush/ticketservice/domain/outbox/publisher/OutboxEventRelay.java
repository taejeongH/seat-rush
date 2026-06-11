package com.seatrush.ticketservice.domain.outbox.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatrush.ticketservice.domain.outbox.config.OutboxRelayProperties;
import com.seatrush.ticketservice.domain.outbox.entity.OutboxEvent;
import com.seatrush.ticketservice.domain.outbox.service.OutboxClaimService;
import com.seatrush.ticketservice.domain.outbox.service.OutboxResultService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 선점한 Outbox 이벤트를 DB 트랜잭션 밖에서 Kafka로 발행합니다.
 */
@Component
@ConditionalOnProperty(
        prefix = "outbox.relay",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OutboxEventRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventRelay.class);

    private final String workerId = UUID.randomUUID().toString();
    private final OutboxClaimService outboxClaimService;
    private final OutboxResultService outboxResultService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final OutboxRelayProperties properties;

    public OutboxEventRelay(
            OutboxClaimService outboxClaimService,
            OutboxResultService outboxResultService,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            OutboxRelayProperties properties
    ) {
        this.outboxClaimService = outboxClaimService;
        this.outboxResultService = outboxResultService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * 이벤트를 짧은 트랜잭션으로 선점한 뒤 Kafka 발행을 순차 수행합니다.
     */
    @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms:1000}")
    public void publishPendingEvents() {
        for (int count = 0; count < properties.batchSize(); count++) {
            Optional<OutboxEvent> event = outboxClaimService.claimNext(workerId);
            if (event.isEmpty()) {
                return;
            }
            publish(event.get());
        }
    }

    /**
     * Kafka 발행 결과를 별도 트랜잭션으로 PUBLISHED 또는 재시도 상태에 반영합니다.
     */
    private void publish(OutboxEvent event) {
        try {
            JsonNode payload = readPayload(event.getPayload());
            kafkaTemplate.send(
                            event.getTopic(),
                            event.getAggregateId().toString(),
                            payload
                    )
                    .get(properties.sendTimeoutSeconds(), TimeUnit.SECONDS);

            boolean updated = outboxResultService.markPublished(event.getId(), workerId);
            logPublishResult(event, updated);
        } catch (JsonProcessingException exception) {
            outboxResultService.markPermanentFailure(
                    event.getId(),
                    workerId,
                    rootMessage(exception)
            );
            log.error(
                    "Outbox payload is invalid - eventId={}, topic={}",
                    event.getEventId(),
                    event.getTopic(),
                    exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            saveRetryableFailure(event, exception);
        } catch (Exception exception) {
            saveRetryableFailure(event, exception);
        }
    }

    private void saveRetryableFailure(OutboxEvent event, Exception exception) {
        outboxResultService.markRetryableFailure(
                event.getId(),
                workerId,
                rootMessage(exception)
        );
        log.error(
                "Outbox event publish failed - eventId={}, topic={}",
                event.getEventId(),
                event.getTopic(),
                exception
        );
    }

    /**
     * Outbox에 문자열로 저장된 JSON payload를 Kafka 발행 객체로 변환합니다.
     */
    private JsonNode readPayload(String payload) throws JsonProcessingException {
        return objectMapper.readTree(payload);
    }

    private void logPublishResult(OutboxEvent event, boolean updated) {
        if (!updated) {
            log.warn(
                    "Outbox publish result ignored because lease ownership changed - eventId={}",
                    event.getEventId()
            );
            return;
        }

        log.info(
                "Outbox event published - eventId={}, topic={}, aggregateId={}",
                event.getEventId(),
                event.getTopic(),
                event.getAggregateId()
        );
    }

    /**
     * 중첩된 예외에서 가장 근본적인 원인의 메시지를 추출합니다.
     */
    private static String rootMessage(Exception exception) {
        Throwable cause = exception;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }
}
