package com.seatrush.ticketservice.domain.reservation.service;

import com.seatrush.ticketservice.domain.reservation.config.ReservationProperties;
import com.seatrush.ticketservice.domain.reservation.entity.Reservation;
import com.seatrush.ticketservice.domain.reservation.event.model.EntrySlotReleaseReason;
import com.seatrush.ticketservice.domain.reservation.event.publisher.EntrySlotReleaseOutboxWriter;
import com.seatrush.ticketservice.domain.reservation.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 결제 대기 상태(PENDING_PAYMENT)로 결제 제한 기한(expires_at)이 초과된 예매 건들을
 * 주기적으로 탐색하여 만료 처리(Status -> EXPIRED)하는 서비스 클래스입니다.
 * 
 * 주요 설계 특징:
 * 1. 배치 처리: 한 번에 모든 만료 대상 데이터를 조회하면 메모리 부하가 발생하므로 설정된 배치 사이즈만큼 청크 단위로 처리합니다.
 * 2. 분산 환경 동시성 제어 (SKIP LOCKED):
 *    {@code ReservationRepository#findExpirableReservations} 호출 시 native query인 `FOR UPDATE SKIP LOCKED` 구문을 사용하여,
 *    다중 WAS/스케줄러 서버 노드가 동시에 실행되더라도 서로 다른 예매 행(Row)을 잠금 획득하고 처리할 수 있어
 *    경쟁(Lock Contention)이 없고 dead lock이 발생하지 않습니다.
 * 3. 데이터 정합성:
 *    각 배치는 하나의 트랜잭션(@Transactional) 내에서 처리되며, 만료 엔티티 변경 및 대기열 슬롯 반환을 위한
 *    Outbox 이벤트 생성이 동일 트랜잭션으로 묶여 원자성을 보장합니다.
 *    이후 트랜잭션이 성공적으로 commit되면 Redis 좌석 선점이 해제됩니다.
 */
@Service
public class ReservationExpirationService {

    private final ReservationRepository reservationRepository;
    private final ReservationHoldReleaseService holdReleaseService;
    private final EntrySlotReleaseOutboxWriter entrySlotReleaseOutboxWriter;
    private final ReservationProperties properties;

    public ReservationExpirationService(
            ReservationRepository reservationRepository,
            ReservationHoldReleaseService holdReleaseService,
            EntrySlotReleaseOutboxWriter entrySlotReleaseOutboxWriter,
            ReservationProperties properties
    ) {
        this.reservationRepository = reservationRepository;
        this.holdReleaseService = holdReleaseService;
        this.entrySlotReleaseOutboxWriter = entrySlotReleaseOutboxWriter;
        this.properties = properties;
    }

    /**
     * 만료 시한이 도과된 예매를 배치 크기만큼 잠금(FOR UPDATE SKIP LOCKED)하여 조회하고 만료 상태로 전환합니다.
     *
     * @param now 현재 기준 시각
     * @return 이번 배치 실행에서 실제 만료 처리된 예매 건수
     */
    @Transactional
    public int expireBatch(LocalDateTime now) {
        // 1. SKIP LOCKED 쿼리를 통해 다른 노드가 점유하지 않은 만료 대상 예매만 행 잠금(Lock)하여 조회
        List<Reservation> reservations = reservationRepository.findExpirableReservations(
                now,
                properties.expirationBatchSize()
        );
        
        // 2. 조회된 만료 예매 건들에 대해 순차 처리
        reservations.forEach(reservation -> {
            // 예매 상태를 EXPIRED로 변경하고 만료일시 기록
            reservation.expire(now);
            
            // 대기열 서비스(Queue Service)에서 해당 유저의 대기열 진입 슬롯을 반환하도록 Outbox 이벤트 저장
            entrySlotReleaseOutboxWriter.append(
                    reservation,
                    EntrySlotReleaseReason.RESERVATION_EXPIRED,
                    now
            );
            
            // DB 커밋 성공 후에 Redis 상의 좌석 임시 선점(Hold)이 해제되도록 동기화 처리 등록
            holdReleaseService.releaseAfterCommit(reservation.getHoldId());
        });
        
        return reservations.size();
    }
}
