package com.seatrush.ticketservice.domain.reservation.service;

import com.seatrush.ticketservice.domain.seat.service.SeatHoldService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 예매 상태 변경이 DB에 커밋된 뒤 Redis 좌석 선점을 해제합니다.
 */
@Service
public class ReservationHoldReleaseService {

    private static final Logger log = LoggerFactory.getLogger(ReservationHoldReleaseService.class);

    private final SeatHoldService seatHoldService;

    public ReservationHoldReleaseService(SeatHoldService seatHoldService) {
        this.seatHoldService = seatHoldService;
    }

    /**
     * 현재 트랜잭션 커밋 이후 hold를 해제하고, 트랜잭션이 없으면 즉시 해제합니다.
     */
    public void releaseAfterCommit(String holdId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            release(holdId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        release(holdId);
                    }
                }
        );
    }

    private void release(String holdId) {
        try {
            seatHoldService.releaseIfPresent(holdId);
        } catch (RuntimeException exception) {
            log.error("Reservation hold release failed - holdId={}", holdId, exception);
        }
    }
}
