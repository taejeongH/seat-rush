package com.seatrush.ticketservice.domain.seatlayout.repository;

import com.seatrush.ticketservice.domain.seatlayout.entity.SeatLayoutSeat;
import com.seatrush.ticketservice.domain.seatlayout.repository.projection.SeatLayoutSeatQueryProjection;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SeatLayoutSeatRepository extends JpaRepository<SeatLayoutSeat, Long> {

    /**
     * 연습 좌석 목록 응답에 필요한 컬럼만 조회합니다.
     * sectionId와 layoutId를 함께 조건으로 사용해 다른 배치의 구역을 조회하지 못하게 합니다.
     */
    @Query("""
            select new com.seatrush.ticketservice.domain.seatlayout.repository.projection.SeatLayoutSeatQueryProjection(
                seat.id,
                seat.rowName,
                seat.seatNumber
            )
            from SeatLayoutSeat seat
            join seat.section section
            where section.id = :sectionId
              and section.layout.id = :layoutId
            order by seat.sortOrder asc
            """)
    List<SeatLayoutSeatQueryProjection> findQueryProjectionsBySectionIdAndLayoutId(
            @Param("sectionId") Long sectionId,
            @Param("layoutId") Long layoutId
    );

    @EntityGraph(attributePaths = {"section", "section.layout"})
    List<SeatLayoutSeat> findAllByIdIn(List<Long> ids);
}
