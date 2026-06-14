package com.seatrush.notificationconsumer.domain.notification.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(NotificationDeduplicationProperties.class)
public class NotificationConfig {
}
