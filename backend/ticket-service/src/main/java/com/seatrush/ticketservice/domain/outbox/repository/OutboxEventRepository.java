package com.seatrush.ticketservice.domain.outbox.repository;

import com.seatrush.ticketservice.domain.outbox.entity.OutboxEvent;
import com.seatrush.ticketservice.domain.outbox.entity.OutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from OutboxEvent event where event.id = :id")
    Optional<OutboxEvent> findByIdForUpdate(@Param("id") Long id);

    long countByStatus(OutboxStatus status);

    long countByStatusAndProcessingDeadlineBefore(
            OutboxStatus status,
            LocalDateTime deadline
    );

    @Modifying
    @Query(value = """
            DELETE FROM outbox_events
            WHERE status = 'PUBLISHED'
              AND published_at < :cutoff
            ORDER BY id
            LIMIT :batchSize
            """, nativeQuery = true)
    int deletePublishedBatch(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("batchSize") int batchSize
    );
}
