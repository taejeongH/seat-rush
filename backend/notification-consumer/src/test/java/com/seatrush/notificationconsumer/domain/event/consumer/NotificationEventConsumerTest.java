package com.seatrush.notificationconsumer.domain.event.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.seatrush.notificationconsumer.domain.event.model.NotificationEvent;
import com.seatrush.notificationconsumer.domain.event.model.NotificationEventType;
import com.seatrush.notificationconsumer.domain.notification.service.NotificationHandleResult;
import com.seatrush.notificationconsumer.domain.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 알림 처리 완료와 Kafka offset 커밋 순서를 검증합니다.
 */
class NotificationEventConsumerTest {

    /**
     * 예매 완료 알림 처리 후 Kafka offset을 커밋합니다.
     */
    @Test
    void acknowledgeReservationConfirmedAfterNotification() throws Exception {
        verifyConsumption(NotificationEventType.RESERVATION_CONFIRMED);
    }

    /**
     * 결제 실패 알림 처리 후 Kafka offset을 커밋합니다.
     */
    @Test
    void acknowledgePaymentFailedAfterNotification() throws Exception {
        verifyConsumption(NotificationEventType.PAYMENT_FAILED);
    }

    private void verifyConsumption(NotificationEventType type) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
        NotificationService notificationService = mock(NotificationService.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        NotificationEventConsumer consumer =
                new NotificationEventConsumer(objectMapper, notificationService);
        NotificationEvent event = event(type);
        when(notificationService.handle(event, type))
                .thenReturn(NotificationHandleResult.SENT);

        if (type == NotificationEventType.RESERVATION_CONFIRMED) {
            consumer.consumeReservationConfirmed(
                    objectMapper.writeValueAsString(event),
                    acknowledgment
            );
        } else {
            consumer.consumePaymentFailed(
                    objectMapper.writeValueAsString(event),
                    acknowledgment
            );
        }

        var order = inOrder(notificationService, acknowledgment);
        order.verify(notificationService).handle(event, type);
        order.verify(acknowledgment).acknowledge();
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
