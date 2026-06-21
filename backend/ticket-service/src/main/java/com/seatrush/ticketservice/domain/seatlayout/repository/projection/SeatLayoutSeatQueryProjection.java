package com.seatrush.ticketservice.domain.seatlayout.repository.projection;

/**
 * 연습용 좌석 목록 응답에 필요한 정적 좌석 컬럼만 담는 조회 전용 모델입니다.
 */
public record SeatLayoutSeatQueryProjection(
        Long seatId,
        String rowName,
        Integer seatNumber
) {
}
