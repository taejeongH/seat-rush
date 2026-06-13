package com.seatrush.ticketservice.domain.seat.dto.response;

import com.seatrush.ticketservice.domain.seat.entity.SeatSection;

import java.math.BigDecimal;

public record SeatSectionResponseDto(
        Long sectionId,
        String name,
        String grade,
        BigDecimal price
) {

    public static SeatSectionResponseDto from(SeatSection section) {
        return new SeatSectionResponseDto(
                section.getId(),
                section.getName(),
                section.getGrade(),
                section.getPrice()
        );
    }
}
