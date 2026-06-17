package com.seatrush.ticketservice.domain.seatlayout.repository;

import com.seatrush.ticketservice.domain.seatlayout.entity.SeatLayout;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatLayoutRepository extends JpaRepository<SeatLayout, Long> {
}
