package com.seatrush.ticketservice.domain.practice.reservation.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PracticePaymentResponseDto(
        String paymentId,
        Long reservationId,
        BigDecimal amount,
        String status,
        LocalDateTime requestedAt,
        LocalDateTime approvedAt
) {
}
