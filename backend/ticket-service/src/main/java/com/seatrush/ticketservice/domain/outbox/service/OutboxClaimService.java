package com.seatrush.ticketservice.domain.outbox.service;

import com.seatrush.ticketservice.domain.outbox.config.OutboxRelayProperties;
import com.seatrush.ticketservice.domain.outbox.entity.OutboxEvent;
import com.seatrush.ticketservice.domain.outbox.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 발행 가능한 Outbox 이벤트를 짧은 트랜잭션으로 선점합니다.
 */
@Service
public class OutboxClaimService {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxRelayProperties properties;

    public OutboxClaimService(
            OutboxEventRepository outboxEventRepository,
            OutboxRelayProperties properties
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.properties = properties;
    }

    /**
     * PENDING 또는 lease가 만료된 PROCESSING 이벤트에 Worker 소유권을 부여합니다.
     */
    @Transactional
    public Optional<OutboxEvent> claimNext(String workerId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = now.plusSeconds(properties.processingLeaseSeconds());

        return outboxEventRepository.findClaimableEvents(now, 1)
                .stream()
                .filter(event -> event.claim(
                        workerId,
                        now,
                        deadline,
                        properties.maxRetryCount()
                ))
                .findFirst();
    }
}
