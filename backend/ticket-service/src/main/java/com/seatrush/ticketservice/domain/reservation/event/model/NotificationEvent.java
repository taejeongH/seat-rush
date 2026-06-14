package com.seatrush.ticketservice.domain.reservation.event.model;

import com.seatrush.ticketservice.domain.reservation.entity.Reservation;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationEvent(
        UUID eventId,
        NotificationEventType eventType,
        Long userId,
        String email,
        String userName,
        Long reservationId,
        String paymentId,
        LocalDateTime occurredAt
) {

    public static NotificationEvent reservationConfirmed(
            Reservation reservation,
            LocalDateTime occurredAt
    ) {
        return from(
                reservation,
                NotificationEventType.RESERVATION_CONFIRMED,
                occurredAt
        );
    }

    public static NotificationEvent paymentFailed(
            Reservation reservation,
            LocalDateTime occurredAt
    ) {
        return from(
                reservation,
                NotificationEventType.PAYMENT_FAILED,
                occurredAt
        );
    }

    private static NotificationEvent from(
            Reservation reservation,
            NotificationEventType eventType,
            LocalDateTime occurredAt
    ) {
        return new NotificationEvent(
                UUID.randomUUID(),
                eventType,
                reservation.getUser().getId(),
                reservation.getUser().getEmail(),
                reservation.getUser().getName(),
                reservation.getId(),
                reservation.getPaymentId(),
                occurredAt
        );
    }
}
