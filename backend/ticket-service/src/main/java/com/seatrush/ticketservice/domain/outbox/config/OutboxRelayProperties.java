package com.seatrush.ticketservice.domain.outbox.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * application.yml의 Outbox Relay 실행 설정을 타입이 있는 객체로 제공합니다.
 */
@Validated
@ConfigurationProperties(prefix = "outbox.relay")
public record OutboxRelayProperties(
        @Min(1) int batchSize,
        @Min(0) int maxRetryCount,
        @Min(1) int sendTimeoutSeconds,
        @Min(1) int processingLeaseSeconds
) {

    @AssertTrue(message = "processingLeaseSeconds는 sendTimeoutSeconds보다 커야 합니다.")
    public boolean isLeaseLongerThanSendTimeout() {
        return processingLeaseSeconds > sendTimeoutSeconds;
    }
}
