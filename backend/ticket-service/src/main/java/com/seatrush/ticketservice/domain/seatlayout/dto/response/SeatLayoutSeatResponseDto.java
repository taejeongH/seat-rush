package com.seatrush.ticketservice.domain.seatlayout.dto.response;

import com.seatrush.ticketservice.domain.seat.dto.response.SeatAvailability;
import com.seatrush.ticketservice.domain.seatlayout.entity.SeatLayoutSeat;

public record SeatLayoutSeatResponseDto(
        Long seatId,
        Long sectionId,
        String rowName,
        Integer seatNumber,
        SeatAvailability status
) {

    public static SeatLayoutSeatResponseDto from(SeatLayoutSeat seat, boolean held) {
        return new SeatLayoutSeatResponseDto(
                seat.getId(),
                seat.getSection().getId(),
                seat.getRowName(),
                seat.getSeatNumber(),
                held ? SeatAvailability.HELD : SeatAvailability.AVAILABLE
        );
    }
}
