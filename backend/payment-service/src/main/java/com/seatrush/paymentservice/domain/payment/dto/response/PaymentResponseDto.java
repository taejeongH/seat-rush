package com.seatrush.paymentservice.domain.payment.dto.response;

import com.seatrush.paymentservice.domain.payment.entity.Payment;
import com.seatrush.paymentservice.domain.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponseDto(
        String paymentId,
        Long reservationId,
        BigDecimal amount,
        PaymentStatus status,
        LocalDateTime completedAt,
        LocalDateTime createdAt
) {

    public static PaymentResponseDto from(Payment payment) {
        return new PaymentResponseDto(
                payment.getId(),
                payment.getReservationId(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getCompletedAt(),
                payment.getCreatedAt()
        );
    }
}
