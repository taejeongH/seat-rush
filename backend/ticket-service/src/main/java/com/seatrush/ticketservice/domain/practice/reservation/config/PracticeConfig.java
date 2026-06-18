package com.seatrush.ticketservice.domain.practice.reservation.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 연습 모드(Practice Mode) 도메인의 설정 빈(Configuration Bean)들을 활성화하는 설정 클래스입니다.
 */
@Configuration
@EnableConfigurationProperties(PracticeProperties.class)
public class PracticeConfig {
}
