package com.seatrush.ticketservice.domain.practice.repository;

import com.seatrush.ticketservice.domain.reservation.dto.response.ReservationSeatResponseDto;
import com.seatrush.ticketservice.domain.reservation.entity.ReservationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PracticeReservationState(
        Long reservationId,
        String practiceSessionId,
        Long scheduleId,
        Long userId,
        String holdId,
        String entryTokenId,
        String paymentId,
        ReservationStatus status,
        BigDecimal totalAmount,
        LocalDateTime expiresAt,
        List<ReservationSeatResponseDto> seats
) {
}
