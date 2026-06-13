package com.seatrush.ticketservice.domain.seat.repository;

import com.seatrush.ticketservice.domain.seat.entity.SeatSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatSectionRepository extends JpaRepository<SeatSection, Long> {

    List<SeatSection> findAllByScheduleIdOrderBySortOrderAsc(Long scheduleId);

    boolean existsByIdAndScheduleId(Long sectionId, Long scheduleId);
}
