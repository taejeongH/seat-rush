package com.seatrush.paymentservice.domain.event.model;

import com.seatrush.paymentservice.domain.payment.entity.Payment;
import com.seatrush.paymentservice.domain.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentResultEvent(
        UUID eventId,
        String paymentId,
        Long reservationId,
        Long userId,
        BigDecimal amount,
        PaymentStatus status,
        LocalDateTime occurredAt
) {

    public static PaymentResultEvent from(Payment payment) {
        return new PaymentResultEvent(
                UUID.randomUUID(),
                payment.getId(),
                payment.getReservationId(),
                payment.getUserId(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getCompletedAt()
        );
    }
}
