package com.seatrush.notificationconsumer.domain.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("notification.deduplication")
public record NotificationDeduplicationProperties(
        Duration processingTtl,
        Duration completedTtl
) {
}
