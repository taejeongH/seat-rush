package com.seatrush.paymentservice.domain.outbox.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatrush.paymentservice.domain.outbox.config.OutboxRelayProperties;
import com.seatrush.paymentservice.domain.outbox.entity.OutboxEvent;
import com.seatrush.paymentservice.domain.outbox.service.OutboxClaimService;
import com.seatrush.paymentservice.domain.outbox.service.OutboxResultService;
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
 * Outbox에 저장된 결제 결과 이벤트를 주기적으로 Kafka에 발행합니다.
 *
 * 이벤트 점유와 발행 결과 저장을 분리해 Kafka 지연이 결제 DB 트랜잭션을 오래 붙잡지 않도록 합니다.
 * 여러 인스턴스가 동시에 실행되어도 worker lease를 통해 같은 이벤트 중복 처리를 줄입니다.
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
     * 설정된 주기마다 발행 가능한 이벤트를 batchSize만큼 점유하고 Kafka로 전송합니다.
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
     * 단일 Outbox 이벤트를 Kafka로 발행하고 결과를 DB 상태에 반영합니다.
     */
    private void publish(OutboxEvent event) {
        try {
            JsonNode payload = objectMapper.readTree(event.getPayload());
            kafkaTemplate.send(
                            event.getTopic(),
                            event.getAggregateId(),
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
                    "Outbox payload가 올바른 JSON 형식이 아닙니다. eventId={}, topic={}",
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

    /**
     * 일시적 실패를 기록해 다음 재시도 시점에 다시 발행할 수 있게 합니다.
     */
    private void saveRetryableFailure(OutboxEvent event, Exception exception) {
        outboxResultService.markRetryableFailure(
                event.getId(),
                workerId,
                rootMessage(exception)
        );
        log.error(
                "Outbox 이벤트 발행에 실패했습니다. eventId={}, topic={}",
                event.getEventId(),
                event.getTopic(),
                exception
        );
    }

    /**
     * 발행 완료 상태 반영 결과를 로그로 남깁니다.
     */
    private void logPublishResult(OutboxEvent event, boolean updated) {
        if (!updated) {
            log.warn(
                    "Outbox 발행 결과가 무시되었습니다. lease 소유권이 변경되었습니다. eventId={}",
                    event.getEventId()
            );
            return;
        }

        log.info(
                "Outbox 이벤트 발행 완료. eventId={}, topic={}, aggregateId={}",
                event.getEventId(),
                event.getTopic(),
                event.getAggregateId()
        );
    }

    /**
     * 중첩 예외에서 가장 안쪽 원인 메시지를 추출합니다.
     */
    private static String rootMessage(Exception exception) {
        Throwable cause = exception;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }
}
