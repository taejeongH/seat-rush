package com.seatrush.queueservice.domain.queue.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        QueueAdmissionProperties.class,
        QueuePracticeProperties.class
})
public class QueueConfig {
}
