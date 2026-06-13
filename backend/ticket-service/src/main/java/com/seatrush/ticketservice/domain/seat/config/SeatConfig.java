package com.seatrush.ticketservice.domain.seat.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SeatHoldProperties.class)
public class SeatConfig {
}
