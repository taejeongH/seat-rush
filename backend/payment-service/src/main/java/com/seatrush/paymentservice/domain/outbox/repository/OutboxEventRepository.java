package com.seatrush.paymentservice.domain.outbox.repository;

import com.seatrush.paymentservice.domain.outbox.entity.OutboxEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * 현재 발행 가능한 이벤트를 조회하면서 동시에 row lock을 획득합니다.
     *
     * SKIP LOCKED를 사용해 다른 worker가 이미 점유한 이벤트는 건너뛰므로,
     * 여러 payment-service 인스턴스가 떠 있어도 서로 다른 이벤트를 나누어 처리할 수 있습니다.
     */
    @Query(value = """
            SELECT *
            FROM outbox_events
            WHERE (status = 'PENDING' AND next_retry_at <= :now)
               OR (status = 'PROCESSING' AND processing_deadline <= :now)
            ORDER BY id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> findClaimableEvents(
            @Param("now") LocalDateTime now,
            @Param("batchSize") int batchSize
    );

    /**
     * 발행 결과 저장 중 현재 worker의 상태 변경이 충돌하지 않도록 단건 row lock을 획득합니다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from OutboxEvent event where event.id = :id")
    Optional<OutboxEvent> findByIdForUpdate(@Param("id") Long id);
}
