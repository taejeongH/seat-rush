package com.seatrush.virtualuser.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("virtual-user")
public record VirtualUserProperties(
        String gatewayBaseUrl,
        Duration requestTimeout,
        Duration queuePollInterval,
        Duration queueHeartbeatInterval,
        Duration queueWaitTimeout,
        Duration paymentPollInterval,
        Duration paymentWaitTimeout,
        Duration actionDelayMin,
        Duration actionDelayMax,
        int seatRetryCount,
        Duration seatRetryDelay,
        int maxSeatsPerUser,
        String accountPoolFile,
        String accountEmailDomain,
        String accountPassword,
        Duration tokenRefreshBuffer,
        Duration preparationLeadTime
) {
}
