package com.seatrush.paymentservice.domain.outbox.service;

import com.seatrush.paymentservice.domain.outbox.config.OutboxRelayProperties;
import com.seatrush.paymentservice.domain.outbox.entity.OutboxEvent;
import com.seatrush.paymentservice.domain.outbox.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Kafka 발행 결과를 Outbox 이벤트 상태에 반영합니다.
 */
@Service
public class OutboxResultService {

    private static final Logger log = LoggerFactory.getLogger(OutboxResultService.class);

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxRelayProperties properties;

    public OutboxResultService(
            OutboxEventRepository outboxEventRepository,
            OutboxRelayProperties properties
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.properties = properties;
    }

    /**
     * 현재 worker가 점유한 이벤트를 PUBLISHED 상태로 변경합니다.
     */
    @Transactional
    public boolean markPublished(Long eventId, String workerId) {
        OutboxEvent event = findForUpdate(eventId);
        return event.markPublished(workerId, LocalDateTime.now());
    }

    /**
     * 일시적인 발행 실패를 기록하고 다음 재시도 시각을 갱신합니다.
     */
    @Transactional
    public boolean markRetryableFailure(
            Long eventId,
            String workerId,
            String errorMessage
    ) {
        OutboxEvent event = findForUpdate(eventId);
        return event.markRetryableFailure(
                workerId,
                errorMessage,
                properties.maxRetryCount(),
                LocalDateTime.now()
        );
    }

    /**
     * 재시도해도 의미 없는 실패를 FAILED 상태로 확정합니다.
     */
    @Transactional
    public boolean markPermanentFailure(
            Long eventId,
            String workerId,
            String errorMessage
    ) {
        OutboxEvent event = findForUpdate(eventId);
        return event.markPermanentFailure(workerId, errorMessage, LocalDateTime.now());
    }

    private OutboxEvent findForUpdate(Long eventId) {
        return outboxEventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> {
                    log.error("Outbox 발행 결과 저장 중 이벤트를 찾을 수 없습니다. eventId={}", eventId);
                    return new IllegalStateException("Outbox 이벤트를 찾을 수 없습니다.");
                });
    }
}
