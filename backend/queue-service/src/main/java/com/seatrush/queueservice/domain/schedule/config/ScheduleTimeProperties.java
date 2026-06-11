package com.seatrush.queueservice.domain.schedule.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.ZoneId;

/**
 * 회차의 LocalDateTime을 epoch milliseconds로 변환할 기준 시간대를 제공합니다.
 */
@Validated
@ConfigurationProperties(prefix = "schedule")
public record ScheduleTimeProperties(
        @NotBlank String timeZone
) {

    public ZoneId zoneId() {
        return ZoneId.of(timeZone);
    }
}
