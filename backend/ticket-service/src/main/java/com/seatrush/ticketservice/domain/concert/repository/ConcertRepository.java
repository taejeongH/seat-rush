package com.seatrush.ticketservice.domain.concert.repository;

import com.seatrush.ticketservice.domain.concert.entity.Concert;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConcertRepository extends JpaRepository<Concert, Long> {
}
