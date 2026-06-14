package com.seatrush.notificationconsumer.domain.notification.service;

import com.seatrush.notificationconsumer.domain.event.model.NotificationEvent;
import com.seatrush.notificationconsumer.domain.event.model.NotificationEventType;
import com.seatrush.notificationconsumer.domain.notification.repository.NotificationClaimResult;
import com.seatrush.notificationconsumer.domain.notification.repository.NotificationDeduplicationRepository;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private final NotificationDeduplicationRepository deduplicationRepository;
    private final MockNotificationSender notificationSender;

    public NotificationService(
            NotificationDeduplicationRepository deduplicationRepository,
            MockNotificationSender notificationSender
    ) {
        this.deduplicationRepository = deduplicationRepository;
        this.notificationSender = notificationSender;
    }

    /**
     * 이벤트 ID로 중복 발송을 차단하고 Mock 알림을 한 번만 발송합니다.
     */
    public NotificationHandleResult handle(
            NotificationEvent event,
            NotificationEventType expectedType
    ) {
        validate(event, expectedType);
        NotificationClaimResult claim =
                deduplicationRepository.claim(event.eventId());
        if (claim == NotificationClaimResult.COMPLETED) {
            return NotificationHandleResult.DUPLICATE;
        }
        if (claim == NotificationClaimResult.PROCESSING) {
            throw new IllegalStateException("알림 이벤트가 이미 처리 중입니다.");
        }

        try {
            notificationSender.send(event);
            deduplicationRepository.complete(event.eventId());
            return NotificationHandleResult.SENT;
        } catch (RuntimeException exception) {
            deduplicationRepository.release(event.eventId());
            throw exception;
        }
    }

    private void validate(
            NotificationEvent event,
            NotificationEventType expectedType
    ) {
        if (event == null
                || event.eventId() == null
                || event.eventType() != expectedType
                || event.userId() == null
                || event.email() == null
                || event.email().isBlank()
                || event.reservationId() == null
                || event.paymentId() == null
                || event.occurredAt() == null) {
            throw new IllegalArgumentException("알림 이벤트 형식이 올바르지 않습니다.");
        }
    }
}
