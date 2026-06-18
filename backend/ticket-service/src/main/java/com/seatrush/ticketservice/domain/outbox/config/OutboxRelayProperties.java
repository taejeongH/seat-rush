package com.seatrush.ticketservice.domain.outbox.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * application.yml의 Outbox Relay 실행 설정을 타입이 있는 객체로 제공합니다.
 * 
 * @param batchSize 한 번에 조회하여 Kafka 브로커로 전송할 아웃박스 이벤트의 최대 배치 레코드 수 (기본값: 100개).
 * @param maxRetryCount Kafka 전송 실패 시 아웃박스 처리를 재시도할 최대 제한 횟수 (기본값: 5회). 이 횟수를 초과하면 영구 실패로 분류됩니다.
 * @param sendTimeoutSeconds Kafka 브로커로 메시지를 비동기 발송 후 응답을 대기하는 최대 타임아웃 시간 (초 단위, 기본값: 10초).
 * @param processingLeaseSeconds 다중 워커 분산 환경에서 특정 아웃박스 레코드를 선점하여 잠그는 임대 계약 시간 (초 단위, 기본값: 30초).
 *                               서버 장애 등으로 이 시간 동안 처리 완료를 못 하면 다른 노드에 의해 재수행됩니다.
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
