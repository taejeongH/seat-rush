package com.seatrush.ticketservice.domain.outbox.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 발행 완료 Outbox 이벤트의 보관 및 배치 삭제 설정을 제공합니다.
 * 
 * @param retentionDays 발행 완료된 Outbox 이벤트를 DB에 보관할 기간 (일 단위, 기본값: 7일). 이 기간이 지난 완료 이벤트는 정리 대상이 됩니다.
 * @param batchSize DB I/O 부하를 분산시키기 위해 한 번의 DELETE 쿼리로 삭제할 최대 레코드 수 (기본값: 1000개).
 * @param maxBatchesPerRun 1회 정리 작업 실행 시 반복 수행할 최대 배치 삭제 횟수 (기본값: 100회). 대량 삭제 시 트랜잭션 락이 길어지는 것을 방지합니다.
 */
@Validated
@ConfigurationProperties(prefix = "outbox.cleanup")
public record OutboxCleanupProperties(
        @Min(1) int retentionDays,
        @Min(1) int batchSize,
        @Min(1) int maxBatchesPerRun
) {
}
