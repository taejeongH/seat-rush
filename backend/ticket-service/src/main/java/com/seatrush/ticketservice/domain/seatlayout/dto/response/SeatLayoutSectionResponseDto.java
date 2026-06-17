package com.seatrush.ticketservice.domain.seatlayout.dto.response;

import com.seatrush.ticketservice.domain.seatlayout.entity.SeatLayoutSection;

import java.math.BigDecimal;

public record SeatLayoutSectionResponseDto(
        Long sectionId,
        String name,
        String grade,
        BigDecimal price
) {

    public static SeatLayoutSectionResponseDto from(SeatLayoutSection section) {
        return new SeatLayoutSectionResponseDto(
                section.getId(),
                section.getName(),
                section.getGrade(),
                section.getPrice()
        );
    }
}
