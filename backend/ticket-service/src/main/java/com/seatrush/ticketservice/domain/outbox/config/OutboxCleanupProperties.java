package com.seatrush.ticketservice.domain.outbox.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 발행 완료 Outbox 이벤트의 보관 및 배치 삭제 설정을 제공합니다.
 */
@Validated
@ConfigurationProperties(prefix = "outbox.cleanup")
public record OutboxCleanupProperties(
        @Min(1) int retentionDays,
        @Min(1) int batchSize,
        @Min(1) int maxBatchesPerRun
) {
}
