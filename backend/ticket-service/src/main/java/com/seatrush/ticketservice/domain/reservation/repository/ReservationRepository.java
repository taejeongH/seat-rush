package com.seatrush.ticketservice.domain.reservation.repository;

import com.seatrush.ticketservice.domain.reservation.entity.Reservation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    boolean existsByHoldId(String holdId);

    @EntityGraph(attributePaths = {
            "schedule",
            "seats",
            "seats.seat",
            "seats.seat.section"
    })
    Optional<Reservation> findByIdAndUserId(Long reservationId, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"schedule", "user"})
    @Query("""
            select reservation
            from Reservation reservation
            where reservation.id = :reservationId
              and reservation.user.id = :userId
            """)
    Optional<Reservation> findByIdAndUserIdForUpdate(
            @Param("reservationId") Long reservationId,
            @Param("userId") Long userId
    );

    @Query(value = """
            SELECT *
            FROM reservations
            WHERE status = 'PENDING_PAYMENT'
              AND expires_at <= :now
            ORDER BY id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Reservation> findExpirableReservations(
            @Param("now") LocalDateTime now,
            @Param("batchSize") int batchSize
    );
}
