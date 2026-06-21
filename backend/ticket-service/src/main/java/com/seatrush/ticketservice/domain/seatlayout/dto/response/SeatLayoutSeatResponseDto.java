package com.seatrush.ticketservice.domain.seatlayout.dto.response;

import com.seatrush.ticketservice.domain.seat.dto.response.SeatAvailability;
import com.seatrush.ticketservice.domain.seatlayout.repository.projection.SeatLayoutSeatQueryProjection;

public record SeatLayoutSeatResponseDto(
        Long seatId,
        Long sectionId,
        String rowName,
        Integer seatNumber,
        SeatAvailability status
) {

    /**
     * 연습 좌석 목록 projection과 요청 구역 ID를 응답 DTO로 변환합니다.
     */
    public static SeatLayoutSeatResponseDto from(
            SeatLayoutSeatQueryProjection seat,
            Long sectionId,
            boolean held
    ) {
        return new SeatLayoutSeatResponseDto(
                seat.seatId(),
                sectionId,
                seat.rowName(),
                seat.seatNumber(),
                held ? SeatAvailability.HELD : SeatAvailability.AVAILABLE
        );
    }
}
