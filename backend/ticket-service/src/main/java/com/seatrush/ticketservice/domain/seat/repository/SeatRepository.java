package com.seatrush.ticketservice.domain.seat.repository;

import com.seatrush.ticketservice.domain.seat.entity.Seat;
import com.seatrush.ticketservice.domain.seat.repository.projection.SeatQueryProjection;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    /**
     * 좌석 목록 응답에 필요한 컬럼만 조회합니다.
     * sectionId와 scheduleId를 함께 조건으로 사용해 다른 회차의 구역을 조회하지 못하게 합니다.
     */
    @Query("""
            select new com.seatrush.ticketservice.domain.seat.repository.projection.SeatQueryProjection(
                seat.id,
                seat.rowName,
                seat.seatNumber,
                seat.status
            )
            from Seat seat
            join seat.section section
            where section.id = :sectionId
              and section.schedule.id = :scheduleId
            order by seat.rowName asc, seat.seatNumber asc
            """)
    List<SeatQueryProjection> findQueryProjectionsBySectionIdAndScheduleId(
            @Param("sectionId") Long sectionId,
            @Param("scheduleId") Long scheduleId
    );

    @EntityGraph(attributePaths = {"section", "section.schedule"})
    List<Seat> findAllByIdIn(List<Long> seatIds);
}
