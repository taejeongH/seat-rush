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
 * DB에 임시 저장된 Outbox 이벤트들을 주기적으로 감지하여 Kafka 브로커로 안전하게 발행하는 릴레이 컴포넌트입니다.
 * 
 * 다중 인스턴스 환경에서 분산 락(Worker Lease) 매커니즘을 적용해 중복 처리를 방지하며,
 * 전송 성공 시 PUBLISHED 마킹, 일시적 전송 지연 시 재시도 대기, 복구 불가 실패 시 FAILED 처리를 수행합니다.
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

    // 각 인스턴스(컨테이너)를 고유 식별하기 위한 Worker ID 무작위 발급
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
     * 지정된 시간 간격(기본 1초)마다 미발행 상태의 이벤트를 순차 선점(Lease)하여 Kafka 브로커에 발행합니다.
     * 배치 사이즈 설정에 따라 한 번에 복수의 미발행 이벤트를 처리할 수 있습니다.
     */
    @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms:1000}")
    public void publishPendingEvents() {
        for (int count = 0; count < properties.batchSize(); count++) {
            // 짧은 트랜잭션 단위로 타겟 이벤트 하나를 점유(Claim)
            Optional<OutboxEvent> event = outboxClaimService.claimNext(workerId);
            if (event.isEmpty()) {
                return; // 더 이상 처리할 대기 이벤트가 없는 경우 조기 종료
            }
            publish(event.get());
        }
    }

    /**
     * 단일 아웃박스 이벤트를 Kafka 브로커로 비동기 송신하고 그에 따른 결과를 DB 상태에 반영합니다.
     */
    private void publish(OutboxEvent event) {
        try {
            // DB에 저장되어 있던 JSON 문자열 데이터를 실제 카프카 메시지 전송용 객체로 역직렬화
            JsonNode payload = readPayload(event.getPayload());
            
            // Kafka Template을 사용해 지정된 토픽 및 키(Aggregate ID)로 메시지 송신 후 대기(Timeout 제한)
            kafkaTemplate.send(
                            event.getTopic(),
                            event.getAggregateId().toString(),
                            payload
                    )
                    .get(properties.sendTimeoutSeconds(), TimeUnit.SECONDS);

            // 정상 전송 시 PUBLISHED 상태로 변경
            boolean updated = outboxResultService.markPublished(event.getId(), workerId);
            logPublishResult(event, updated);
        } catch (JsonProcessingException exception) {
            // JSON 역직렬화 실패 등 복구 불가능한 영구 결함 시 즉시 FAILED 상태로 강제 전환
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
            Thread.currentThread().interrupt(); // 인터럽트 비트 재설정
            saveRetryableFailure(event, exception);
        } catch (Exception exception) {
            // 네트워크 순시 장애 등 일시적 실패는 추후 지수 백오프 기반 재시도 진행
            saveRetryableFailure(event, exception);
        }
    }

    /**
     * 일시적 전송 장애 상황을 재시도 가능 실패 상태로 보관 처리합니다.
     */
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

