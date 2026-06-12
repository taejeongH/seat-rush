package com.seatrush.queueservice.domain.entrytoken.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "entry-token")
public record EntryTokenProperties(
        @NotNull Duration ttl,
        String issuer,
        String audience,
        Resource privateKeyLocation,
        Resource publicKeyLocation
) {
}
