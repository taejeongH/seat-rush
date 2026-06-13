package com.seatrush.ticketservice.domain.reservation.scheduler;

import com.seatrush.ticketservice.domain.reservation.service.ReservationExpirationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 결제 기한이 지난 예매를 주기적으로 찾아 만료 처리합니다.
 */
@Component
public class ReservationExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpirationScheduler.class);

    private final ReservationExpirationService expirationService;

    public ReservationExpirationScheduler(ReservationExpirationService expirationService) {
        this.expirationService = expirationService;
    }

    /**
     * 설정한 주기마다 만료 예매 한 배치를 처리합니다.
     */
    @Scheduled(fixedDelayString = "${reservation.expiration-fixed-delay-ms:1000}")
    public void expireReservations() {
        int expiredCount = expirationService.expireBatch(LocalDateTime.now());
        if (expiredCount > 0) {
            log.info("Pending reservations expired - count={}", expiredCount);
        }
    }
}
