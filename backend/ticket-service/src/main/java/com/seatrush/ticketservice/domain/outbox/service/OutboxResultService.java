package com.seatrush.ticketservice.domain.outbox.service;

import com.seatrush.ticketservice.domain.outbox.config.OutboxRelayProperties;
import com.seatrush.ticketservice.domain.outbox.entity.OutboxEvent;
import com.seatrush.ticketservice.domain.outbox.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Kafka 발행 결과를 소유권을 확인한 뒤 짧은 트랜잭션으로 저장합니다.
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
     * 현재 Worker가 소유한 이벤트를 PUBLISHED 상태로 변경합니다.
     */
    @Transactional
    public boolean markPublished(Long eventId, String workerId) {
        OutboxEvent event = findForUpdate(eventId);
        return event.markPublished(workerId, LocalDateTime.now());
    }

    /**
     * 재시도 가능한 발행 실패를 백오프 정보와 함께 기록합니다.
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
     * 재시도할 수 없는 발행 실패를 즉시 FAILED 상태로 기록합니다.
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
                    log.error("Outbox event not found while saving publish result - eventId={}", eventId);
                    return new IllegalStateException("Outbox 이벤트를 찾을 수 없습니다.");
                });
    }
}
