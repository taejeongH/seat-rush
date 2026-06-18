package com.seatrush.ticketservice.domain.reservation.scheduler;

import com.seatrush.ticketservice.domain.reservation.service.ReservationExpirationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 결제 기한이 지난 예매 건들을 주기적으로 탐색 및 정리하기 위한 스케줄러 컴포넌트입니다.
 * 
 * 주요 설계 특징:
 * 1. 주기적 실행: `fixedDelay` 주기로 동작하며, 하나의 태스크 실행이 끝난 후 설정된 지연 시간(기본값 1초) 후에 다음 태스크가 시작됩니다.
 * 2. 분산 스케줄러 안전성: DB 레벨에서 `SKIP LOCKED` 방식을 활용하여 별도의 분산 락(Distributed Lock)
 *    프레임워크(ShedLock 등) 없이도 다중 인스턴스 환경에서 안전하고 중복 없이 만료 작업을 스케일 아웃할 수 있습니다.
 */
@Component
public class ReservationExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpirationScheduler.class);

    private final ReservationExpirationService expirationService;

    public ReservationExpirationScheduler(ReservationExpirationService expirationService) {
        this.expirationService = expirationService;
    }

    /**
     * 주기적으로 만료 대상 예매 배치를 1회 처리합니다.
     * 처리된 건수가 존재할 때에만 완료 정보 로그를 출력합니다.
     */
    @Scheduled(fixedDelayString = "${reservation.expiration-fixed-delay-ms:1000}")
    public void expireReservations() {
        int expiredCount = expirationService.expireBatch(LocalDateTime.now());
        if (expiredCount > 0) {
            log.info("Pending reservations expired - count={}", expiredCount);
        }
    }
}
