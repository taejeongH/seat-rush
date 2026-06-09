package com.seatrush.ticketservice.domain.concert.repository;

import com.seatrush.ticketservice.domain.concert.entity.ConcertSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConcertScheduleRepository extends JpaRepository<ConcertSchedule, Long> {

    List<ConcertSchedule> findAllByConcertIdOrderByPerformanceAtAsc(Long concertId);
}
