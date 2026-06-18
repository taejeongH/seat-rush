package com.seatrush.ticketservice.domain.practice.reservation.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 연습 모드(Practice Mode) 시뮬레이션 관련 설정을 제공하는 프로퍼티 클래스입니다.
 */
@Validated
@ConfigurationProperties(prefix = "practice")
public record PracticeProperties(
        /**
         * 연습 모드 가상 예매 세션의 결과 데이터를 Redis 캐시에 유지(보관)하는 만료 제한 시간(Duration)입니다.
         * 기본값은 3시간(3h)이며, 이 시간이 경과하면 Redis에서 자동으로 연습 세션 관련 데이터가 삭제(만료)됩니다.
         */
        @NotNull Duration sessionResultTtl
) {
}
