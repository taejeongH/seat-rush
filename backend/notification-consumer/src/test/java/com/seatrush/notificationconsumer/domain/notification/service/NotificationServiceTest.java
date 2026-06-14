package com.seatrush.notificationconsumer.domain.notification.service;

import com.seatrush.notificationconsumer.domain.event.model.NotificationEvent;
import com.seatrush.notificationconsumer.domain.event.model.NotificationEventType;
import com.seatrush.notificationconsumer.domain.notification.repository.NotificationClaimResult;
import com.seatrush.notificationconsumer.domain.notification.repository.NotificationDeduplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 이벤트 ID 기반 중복 방지와 Mock 알림 발송 결과를 검증합니다.
 */
class NotificationServiceTest {

    private NotificationDeduplicationRepository deduplicationRepository;
    private MockNotificationSender notificationSender;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        deduplicationRepository = mock(NotificationDeduplicationRepository.class);
        notificationSender = mock(MockNotificationSender.class);
        service = new NotificationService(
                deduplicationRepository,
                notificationSender
        );
    }

    /**
     * 처리 권한을 획득하면 알림을 발송하고 완료 상태를 기록합니다.
     */
    @Test
    void sendAndCompleteNotification() {
        NotificationEvent event =
                event(NotificationEventType.RESERVATION_CONFIRMED);
        when(deduplicationRepository.claim(event.eventId()))
                .thenReturn(NotificationClaimResult.CLAIMED);

        NotificationHandleResult result = service.handle(
                event,
                NotificationEventType.RESERVATION_CONFIRMED
        );

        assertThat(result).isEqualTo(NotificationHandleResult.SENT);
        verify(notificationSender).send(event);
        verify(deduplicationRepository).complete(event.eventId());
    }

    /**
     * 이미 완료된 이벤트는 알림을 다시 발송하지 않습니다.
     */
    @Test
    void skipCompletedDuplicateEvent() {
        NotificationEvent event =
                event(NotificationEventType.PAYMENT_FAILED);
        when(deduplicationRepository.claim(event.eventId()))
                .thenReturn(NotificationClaimResult.COMPLETED);

        NotificationHandleResult result = service.handle(
                event,
                NotificationEventType.PAYMENT_FAILED
        );

        assertThat(result).isEqualTo(NotificationHandleResult.DUPLICATE);
        verify(notificationSender, never()).send(event);
    }

    /**
     * 다른 Consumer가 처리 중이면 Kafka 재시도를 위해 실패를 반환합니다.
     */
    @Test
    void retryEventAlreadyBeingProcessed() {
        NotificationEvent event =
                event(NotificationEventType.PAYMENT_FAILED);
        when(deduplicationRepository.claim(event.eventId()))
                .thenReturn(NotificationClaimResult.PROCESSING);

        assertThatThrownBy(() -> service.handle(
                event,
                NotificationEventType.PAYMENT_FAILED
        )).isInstanceOf(IllegalStateException.class);
    }

    /**
     * 알림 발송이 실패하면 처리 키를 해제해 다음 재시도를 허용합니다.
     */
    @Test
    void releaseClaimAfterNotificationFailure() {
        NotificationEvent event =
                event(NotificationEventType.RESERVATION_CONFIRMED);
        when(deduplicationRepository.claim(event.eventId()))
                .thenReturn(NotificationClaimResult.CLAIMED);
        org.mockito.Mockito.doThrow(new IllegalStateException("send failed"))
                .when(notificationSender)
                .send(event);

        assertThatThrownBy(() -> service.handle(
                event,
                NotificationEventType.RESERVATION_CONFIRMED
        )).isInstanceOf(IllegalStateException.class);
        verify(deduplicationRepository).release(event.eventId());
    }

    private NotificationEvent event(NotificationEventType type) {
        return new NotificationEvent(
                UUID.randomUUID(),
                type,
                10L,
                "user@example.com",
                "user",
                100L,
                "payment-1",
                LocalDateTime.now()
        );
    }
}
