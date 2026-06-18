package com.seatrush.ticketservice.domain.practice.reservation.repository;

import com.seatrush.ticketservice.domain.reservation.dto.response.ReservationSeatResponseDto;
import com.seatrush.ticketservice.domain.reservation.entity.ReservationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 연습 모드에서 진행 중인 가상 예매의 상태를 Redis에 캐싱하기 위한 레코드(DTO) 클래스입니다.
 * 
 * 실제 DB의 Reservation 엔티티 역할을 대신 수행하며, 가상 결제 상태 및 좌석 정보를 보관합니다.
 */
public record PracticeReservationState(
        Long reservationId,         // 가상 예매 고유 ID (Redis SEQUENCE로 발급)
        String practiceSessionId,   // 현재 연습 세션 식별 ID
        Long scheduleId,            // 예약하려는 공연 회차 ID
        Long userId,                // 연습에 진입한 실제 사용자 ID
        String holdId,              // Redis 좌석 선점 식별 키
        String entryTokenId,        // 사용자가 제출한 대기열 진입 토큰 ID
        String paymentId,           // 가상 결제 세션을 위한 식별 ID
        ReservationStatus status,   // 현재 가상 예매 상태 (PENDING_PAYMENT, CONFIRMED, CANCELED 등)
        BigDecimal totalAmount, // 결제 총 금액
        LocalDateTime expiresAt, // 결제 제한 만료 일시
        List<ReservationSeatResponseDto> seats // 예약 좌석 리스트
) {
}
