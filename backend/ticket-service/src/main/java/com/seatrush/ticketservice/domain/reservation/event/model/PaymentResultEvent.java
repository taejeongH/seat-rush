package com.seatrush.ticketservice.domain.reservation.event.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentResultEvent(
        UUID eventId,
        String paymentId,
        Long reservationId,
        Long userId,
        BigDecimal amount,
        PaymentResultStatus status,
        LocalDateTime occurredAt
) {
}
