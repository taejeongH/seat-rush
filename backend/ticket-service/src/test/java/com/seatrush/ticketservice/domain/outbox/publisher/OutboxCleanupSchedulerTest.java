package com.seatrush.ticketservice.domain.outbox.publisher;

import com.seatrush.ticketservice.domain.outbox.config.OutboxCleanupProperties;
import com.seatrush.ticketservice.domain.outbox.service.OutboxCleanupService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 발행 완료 Outbox 이벤트의 배치 정리 반복을 검증합니다.
 */
class OutboxCleanupSchedulerTest {

    /**
     * 한 배치가 가득 삭제되면 남은 이벤트를 확인하기 위해 다음 배치를 실행합니다.
     */
    @Test
    void repeatCleanupWhileBatchIsFull() {
        OutboxCleanupService cleanupService = mock(OutboxCleanupService.class);
        when(cleanupService.deletePublishedBatch(any(LocalDateTime.class), org.mockito.ArgumentMatchers.eq(1000)))
                .thenReturn(1000, 5);
        OutboxCleanupScheduler scheduler = new OutboxCleanupScheduler(
                cleanupService,
                new OutboxCleanupProperties(7, 1000, 100)
        );

        scheduler.cleanupPublishedEvents();

        verify(cleanupService, times(2))
                .deletePublishedBatch(any(LocalDateTime.class), org.mockito.ArgumentMatchers.eq(1000));
    }
}
