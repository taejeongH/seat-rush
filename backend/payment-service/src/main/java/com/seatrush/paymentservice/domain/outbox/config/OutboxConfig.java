package com.seatrush.paymentservice.domain.outbox.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Outbox Relay 스케줄러와 설정 바인딩을 활성화합니다.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(OutboxRelayProperties.class)
public class OutboxConfig {
}
