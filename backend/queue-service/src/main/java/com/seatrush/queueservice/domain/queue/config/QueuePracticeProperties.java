package com.seatrush.queueservice.domain.queue.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "queue.practice")
public record QueuePracticeProperties(
        @NotNull Duration dataTtl
) {
}
