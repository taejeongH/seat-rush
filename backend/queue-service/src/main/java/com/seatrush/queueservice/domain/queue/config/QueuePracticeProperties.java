package com.seatrush.queueservice.domain.queue.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "queue.practice")
public record QueuePracticeProperties(
        @NotNull Duration dataTtl,
        @NotNull Duration ttlRefreshInterval
) {
    @AssertTrue(message = "TTL 갱신 간격은 연습 세션 데이터 TTL보다 짧아야 합니다.")
    public boolean isTtlRefreshIntervalValid() {
        return dataTtl != null
                && ttlRefreshInterval != null
                && !ttlRefreshInterval.isNegative()
                && !ttlRefreshInterval.isZero()
                && ttlRefreshInterval.compareTo(dataTtl) < 0;
    }
}
