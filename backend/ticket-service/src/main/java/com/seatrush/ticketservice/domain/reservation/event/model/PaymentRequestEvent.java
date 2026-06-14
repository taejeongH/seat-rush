package com.seatrush.ticketservice.domain.reservation.event.model;

import com.seatrush.ticketservice.domain.reservation.entity.Reservation;

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

    public static PaymentRequestEvent from(
            Reservation reservation,
            LocalDateTime requestedAt
    ) {
        return new PaymentRequestEvent(
                UUID.randomUUID(),
                reservation.getPaymentId(),
                reservation.getId(),
                reservation.getUser().getId(),
                reservation.getTotalAmount(),
                requestedAt
        );
    }
}
