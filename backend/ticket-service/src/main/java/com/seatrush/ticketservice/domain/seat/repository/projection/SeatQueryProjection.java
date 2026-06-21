package com.seatrush.ticketservice.domain.seat.repository.projection;

import com.seatrush.ticketservice.domain.seat.entity.SeatStatus;

/**
 * 좌석 목록 응답에 필요한 정적 좌석 컬럼만 담는 조회 전용 모델입니다.
 */
public record SeatQueryProjection(
        Long seatId,
        String rowName,
        Integer seatNumber,
        SeatStatus status
) {
}
