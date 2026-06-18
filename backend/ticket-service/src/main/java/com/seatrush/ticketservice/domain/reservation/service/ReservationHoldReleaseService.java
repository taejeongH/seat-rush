package com.seatrush.ticketservice.domain.reservation.service;

import com.seatrush.ticketservice.domain.seat.service.SeatHoldService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 예매 상태 변경(결제 완료, 실패, 만료 등)이 RDB 트랜잭션에 성공적으로 반영된 후,
 * Redis에 선점(Hold)되어 있던 임시 좌석 데이터를 최종 해제하는 서비스입니다.
 * 
 * 주요 설계 특징:
 * 1. 트랜잭션 동기화 (Transaction Synchronization): 
 *    DB 트랜잭션이 최종 `commit`된 직후에 Redis 해제 명령을 전달합니다. 트랜잭션 도중 예외가 발생해 `rollback`될 경우
 *    Redis 선점을 해제하지 않고 유지시켜, DB 상태와 Redis 상태의 일치성을 유지합니다.
 * 2. 비관적 예외 처리: Redis 좌석 선점 해제 과정에서 네트워크 장애 등 예외가 발생하더라도,
 *    이미 완료된 DB 트랜잭션 비즈니스 결과(결제 완료/만료 등)에 영향을 주지 않도록 예외를 로깅 후 흡수합니다.
 */
@Service
public class ReservationHoldReleaseService {

    private static final Logger log = LoggerFactory.getLogger(ReservationHoldReleaseService.class);

    private final SeatHoldService seatHoldService;

    public ReservationHoldReleaseService(SeatHoldService seatHoldService) {
        this.seatHoldService = seatHoldService;
    }

    /**
     * 지정된 Hold ID(Redis Key)의 좌석 선점을 트랜잭션 커밋 완료 후에 해제합니다.
     * 활성화된 트랜잭션이 존재하지 않는 경우 즉시 해제를 수행합니다.
     *
     * @param holdId 해제할 Redis 선점 식별 키
     */
    public void releaseAfterCommit(String holdId) {
        // 현재 스레드에 활성화된 트랜잭션이 없다면 즉시 해제 시도
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            release(holdId);
            return;
        }

        // 트랜잭션이 진행 중인 경우, 커밋 성공 후(afterCommit) 콜백 등록
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        release(holdId);
                    }
                }
        );
    }

    /**
     * 실질적으로 Redis 좌석 선점 해제를 SeatHoldService에 위임하며,
     * 외부 캐시/Redis 저장소 오류가 메인 DB 비즈니스 흐름을 롤백시키지 않도록 예외 처리를 감싸줍니다.
     */
    private void release(String holdId) {
        try {
            seatHoldService.releaseIfPresent(holdId);
        } catch (RuntimeException exception) {
            log.error("Reservation hold release failed - holdId={}", holdId, exception);
        }
    }
}
