package com.seatrush.notificationconsumer.domain.notification.service;

import com.seatrush.notificationconsumer.domain.event.model.NotificationEvent;
import com.seatrush.notificationconsumer.domain.event.model.NotificationEventType;
import com.seatrush.notificationconsumer.domain.notification.repository.NotificationClaimResult;
import com.seatrush.notificationconsumer.domain.notification.repository.NotificationDeduplicationRepository;
import org.springframework.stereotype.Service;

/**
 * 알림 이벤트 검증, 중복 방지, Mock 알림 발송 흐름을 담당합니다.
 */
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
     * 이벤트 타입과 필수 값을 검증한 뒤 중복 발송 방지를 거쳐 알림을 발송합니다.
     *
     * 이미 완료된 이벤트는 성공으로 간주해 Kafka offset을 commit할 수 있게 DUPLICATE를 반환합니다.
     * 처리 중 상태에서 다시 들어온 이벤트는 예외를 던져 Kafka 재시도를 유도합니다.
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

    /**
     * 알림 발송에 필요한 이벤트 필드와 기대 이벤트 타입을 검증합니다.
     */
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
            throw new IllegalArgumentException("알림 이벤트 필수 값이 누락되었습니다.");
        }
    }
}
