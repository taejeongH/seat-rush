package com.seatrush.ticketservice.domain.seat.dto.response;

import com.seatrush.ticketservice.domain.seat.entity.Seat;
import com.seatrush.ticketservice.domain.seat.entity.SeatStatus;

public record SeatResponseDto(
        Long seatId,
        Long sectionId,
        String rowName,
        Integer seatNumber,
        SeatAvailability status
) {

    public static SeatResponseDto from(Seat seat, boolean held) {
        SeatAvailability availability = held && seat.getStatus() == SeatStatus.AVAILABLE
                ? SeatAvailability.HELD
                : fromStatus(seat.getStatus());

        return new SeatResponseDto(
                seat.getId(),
                seat.getSection().getId(),
                seat.getRowName(),
                seat.getSeatNumber(),
                availability
        );
    }

    private static SeatAvailability fromStatus(SeatStatus status) {
        return switch (status) {
            case AVAILABLE -> SeatAvailability.AVAILABLE;
            case RESERVED -> SeatAvailability.RESERVED;
            case BLOCKED -> SeatAvailability.BLOCKED;
        };
    }
}
