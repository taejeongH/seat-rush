package com.seatrush.paymentservice.domain.outbox.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Outbox Relay 동작 방식을 application.yml에서 조정하기 위한 설정입니다.
 *
 * Kafka 지연, 재시도 횟수, worker lease 시간을 운영 환경에 맞게 조절할 수 있습니다.
 */
@Validated
@ConfigurationProperties(prefix = "outbox.relay")
public record OutboxRelayProperties(
        // 한 번의 릴레이 주기에서 처리할 최대 이벤트 수입니다.
        @Min(1) int batchSize,
        // Kafka 발행 실패 시 재시도할 최대 횟수입니다.
        @Min(0) int maxRetryCount,
        // Kafka broker ACK를 기다릴 최대 시간입니다.
        @Min(1) int sendTimeoutSeconds,
        // 특정 worker가 이벤트를 점유할 수 있는 시간입니다.
        @Min(1) int processingLeaseSeconds
) {

    @AssertTrue(message = "processingLeaseSeconds는 sendTimeoutSeconds보다 커야 합니다.")
    public boolean isLeaseLongerThanSendTimeout() {
        return processingLeaseSeconds > sendTimeoutSeconds;
    }
}
