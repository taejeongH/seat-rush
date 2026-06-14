package com.seatrush.paymentservice.domain.event.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentRequestEvent(
        UUID eventId,
        String paymentId,
        Long reservationId,
        Long userId,
        BigDecimal amount,
        LocalDateTime requestedAt
) {
}
