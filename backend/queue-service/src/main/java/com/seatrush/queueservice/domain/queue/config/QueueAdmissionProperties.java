package com.seatrush.queueservice.domain.queue.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "queue.admission")
public record QueueAdmissionProperties(
        @Min(1) int capacity
) {
}
