package com.seatrush.ticketservice.domain.seat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "seat.hold")
public record SeatHoldProperties(
        Duration ttl,
        int maxSeats
) {
}
