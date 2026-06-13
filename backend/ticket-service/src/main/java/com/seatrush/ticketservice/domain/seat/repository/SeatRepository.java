package com.seatrush.ticketservice.domain.seat.repository;

import com.seatrush.ticketservice.domain.seat.entity.Seat;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    @EntityGraph(attributePaths = "section")
    List<Seat> findAllBySectionIdOrderByRowNameAscSeatNumberAsc(Long sectionId);

    @EntityGraph(attributePaths = "section")
    List<Seat> findAllByIdIn(List<Long> seatIds);
}
