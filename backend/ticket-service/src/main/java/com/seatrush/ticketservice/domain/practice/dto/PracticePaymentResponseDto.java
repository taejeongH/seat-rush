package com.seatrush.ticketservice.domain.practice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PracticePaymentResponseDto(
        String paymentId,
        Long reservationId,
        BigDecimal amount,
        String status,
        LocalDateTime completedAt,
        LocalDateTime createdAt
) {
}
