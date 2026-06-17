package com.seatrush.ticketservice.domain.seatlayout.repository;

import com.seatrush.ticketservice.domain.seatlayout.entity.SeatLayoutSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatLayoutSectionRepository extends JpaRepository<SeatLayoutSection, Long> {

    List<SeatLayoutSection> findAllByLayoutIdOrderBySortOrderAsc(Long layoutId);

    boolean existsByIdAndLayoutId(Long id, Long layoutId);
}
