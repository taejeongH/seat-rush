package com.seatrush.ticketservice.domain.seat.dto.response;

import com.seatrush.ticketservice.domain.seat.entity.SeatStatus;
import com.seatrush.ticketservice.domain.seat.repository.projection.SeatQueryProjection;

public record SeatResponseDto(
        Long seatId,
        Long sectionId,
        String rowName,
        Integer seatNumber,
        SeatAvailability status
) {

    /**
     * 좌석 목록 projection과 요청 구역 ID를 응답 DTO로 변환합니다.
     */
    public static SeatResponseDto from(SeatQueryProjection seat, Long sectionId, boolean held) {
        SeatAvailability availability = held && seat.status() == SeatStatus.AVAILABLE
                ? SeatAvailability.HELD
                : fromStatus(seat.status());

        return new SeatResponseDto(
                seat.seatId(),
                sectionId,
                seat.rowName(),
                seat.seatNumber(),
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
