package com.seatrush.notificationconsumer.domain.event.model;

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
}
