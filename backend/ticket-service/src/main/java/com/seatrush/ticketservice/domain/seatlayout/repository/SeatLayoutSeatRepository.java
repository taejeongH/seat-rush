package com.seatrush.ticketservice.domain.seatlayout.repository;

import com.seatrush.ticketservice.domain.seatlayout.entity.SeatLayoutSeat;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatLayoutSeatRepository extends JpaRepository<SeatLayoutSeat, Long> {

    List<SeatLayoutSeat> findAllBySectionIdOrderBySortOrderAsc(Long sectionId);

    @EntityGraph(attributePaths = {"section", "section.layout"})
    List<SeatLayoutSeat> findAllByIdIn(List<Long> ids);
}
