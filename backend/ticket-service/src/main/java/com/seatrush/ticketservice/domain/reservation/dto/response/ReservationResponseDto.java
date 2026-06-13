package com.seatrush.ticketservice.domain.reservation.dto.response;

import com.seatrush.ticketservice.domain.reservation.entity.Reservation;
import com.seatrush.ticketservice.domain.reservation.entity.ReservationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ReservationResponseDto(
        Long reservationId,
        Long scheduleId,
        String holdId,
        ReservationStatus status,
        BigDecimal totalAmount,
        LocalDateTime expiresAt,
        List<ReservationSeatResponseDto> seats
) {

    public static ReservationResponseDto from(Reservation reservation) {
        return new ReservationResponseDto(
                reservation.getId(),
                reservation.getSchedule().getId(),
                reservation.getHoldId(),
                reservation.getStatus(),
                reservation.getTotalAmount(),
                reservation.getExpiresAt(),
                reservation.getSeats().stream()
                        .map(ReservationSeatResponseDto::from)
                        .toList()
        );
    }
}
