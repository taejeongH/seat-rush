package com.seatrush.ticketservice.domain.outbox.service;

import com.seatrush.ticketservice.domain.outbox.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 보관 기간이 지난 발행 완료 Outbox 이벤트를 배치 단위로 삭제합니다.
 */
@Service
public class OutboxCleanupService {

    private final OutboxEventRepository outboxEventRepository;

    public OutboxCleanupService(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    /**
     * 기준 시각보다 오래된 PUBLISHED 이벤트를 최대 배치 크기만큼 삭제합니다.
     */
    @Transactional
    public int deletePublishedBatch(LocalDateTime cutoff, int batchSize) {
        return outboxEventRepository.deletePublishedBatch(cutoff, batchSize);
    }
}
