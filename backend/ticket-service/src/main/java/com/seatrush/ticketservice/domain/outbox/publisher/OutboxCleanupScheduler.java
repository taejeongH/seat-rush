package com.seatrush.ticketservice.domain.outbox.publisher;

import com.seatrush.ticketservice.domain.outbox.config.OutboxCleanupProperties;
import com.seatrush.ticketservice.domain.outbox.service.OutboxCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 보관 기간이 지난 PUBLISHED 이벤트를 매일 정해진 시각에 정리합니다.
 */
@Component
@ConditionalOnProperty(
        prefix = "outbox.cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OutboxCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxCleanupScheduler.class);

    private final OutboxCleanupService outboxCleanupService;
    private final OutboxCleanupProperties properties;

    public OutboxCleanupScheduler(
            OutboxCleanupService outboxCleanupService,
            OutboxCleanupProperties properties
    ) {
        this.outboxCleanupService = outboxCleanupService;
        this.properties = properties;
    }

    /**
     * 보관 기간이 지난 이벤트가 없을 때까지 짧은 삭제 트랜잭션을 반복합니다.
     */
    @Scheduled(
            cron = "${outbox.cleanup.cron:0 0 3 * * *}",
            zone = "${outbox.cleanup.zone:Asia/Seoul}"
    )
    public void cleanupPublishedEvents() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(properties.retentionDays());
        int totalDeleted = 0;
        for (int batch = 0; batch < properties.maxBatchesPerRun(); batch++) {
            int deleted = outboxCleanupService.deletePublishedBatch(
                    cutoff,
                    properties.batchSize()
            );
            totalDeleted += deleted;
            if (deleted < properties.batchSize()) {
                break;
            }
        }

        if (totalDeleted > 0) {
            log.info(
                    "Published outbox events cleaned up - deleted={}, cutoff={}",
                    totalDeleted,
                    cutoff
            );
        }
    }
}
