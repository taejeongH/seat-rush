package com.seatrush.queueservice.domain.schedule.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ScheduleTimeProperties.class)
public class ScheduleConfig {
}
