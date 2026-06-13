package com.seatrush.ticketservice.domain.reservation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "reservation")
public record ReservationProperties(
        Duration paymentTimeout,
        int expirationBatchSize
) {
}
