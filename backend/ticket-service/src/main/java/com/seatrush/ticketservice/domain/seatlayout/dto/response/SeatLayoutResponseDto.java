package com.seatrush.ticketservice.domain.seatlayout.dto.response;

import com.seatrush.ticketservice.domain.seatlayout.entity.SeatLayout;

public record SeatLayoutResponseDto(
        Long seatLayoutId,
        String name,
        String venueName,
        String description,
        Integer totalSeatCount
) {

    public static SeatLayoutResponseDto from(SeatLayout layout) {
        return new SeatLayoutResponseDto(
                layout.getId(),
                layout.getName(),
                layout.getVenueName(),
                layout.getDescription(),
                layout.getTotalSeatCount()
        );
    }
}
