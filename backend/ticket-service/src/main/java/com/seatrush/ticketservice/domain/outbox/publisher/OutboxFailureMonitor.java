package com.seatrush.ticketservice.domain.outbox.publisher;

import com.seatrush.ticketservice.domain.outbox.entity.OutboxStatus;
import com.seatrush.ticketservice.domain.outbox.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * FAILED 이벤트와 만료된 PROCESSING 이벤트를 감시해 운영 로그로 알립니다.
 */
@Component
@ConditionalOnProperty(
        prefix = "outbox.monitor",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OutboxFailureMonitor {

    private static final Logger log = LoggerFactory.getLogger(OutboxFailureMonitor.class);

    private final OutboxEventRepository outboxEventRepository;

    public OutboxFailureMonitor(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    /**
     * 운영자의 확인이 필요한 FAILED 및 lease 만료 이벤트 수를 경고 로그로 남깁니다.
     */
    @Scheduled(fixedDelayString = "${outbox.monitor.fixed-delay-ms:60000}")
    public void monitorFailures() {
        long failedCount = outboxEventRepository.countByStatus(OutboxStatus.FAILED);
        long expiredProcessingCount =
                outboxEventRepository.countByStatusAndProcessingDeadlineBefore(
                        OutboxStatus.PROCESSING,
                        LocalDateTime.now()
                );

        if (failedCount > 0 || expiredProcessingCount > 0) {
            log.warn(
                    "Outbox events require attention - failed={}, expiredProcessing={}",
                    failedCount,
                    expiredProcessingCount
            );
        }
    }
}
