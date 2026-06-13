package com.seatrush.ticketservice.domain.reservation.dto.response;

import com.seatrush.ticketservice.domain.reservation.entity.ReservationSeat;

import java.math.BigDecimal;

public record ReservationSeatResponseDto(
        Long seatId,
        Long sectionId,
        String sectionName,
        String rowName,
        Integer seatNumber,
        BigDecimal price
) {

    public static ReservationSeatResponseDto from(ReservationSeat reservationSeat) {
        var seat = reservationSeat.getSeat();
        return new ReservationSeatResponseDto(
                seat.getId(),
                seat.getSection().getId(),
                seat.getSection().getName(),
                seat.getRowName(),
                seat.getSeatNumber(),
                reservationSeat.getPrice()
        );
    }
}
