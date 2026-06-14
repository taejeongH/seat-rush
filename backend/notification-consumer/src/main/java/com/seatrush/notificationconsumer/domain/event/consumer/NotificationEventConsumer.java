package com.seatrush.notificationconsumer.domain.event.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatrush.notificationconsumer.common.kafka.KafkaTopic;
import com.seatrush.notificationconsumer.domain.event.model.NotificationEvent;
import com.seatrush.notificationconsumer.domain.event.model.NotificationEventType;
import com.seatrush.notificationconsumer.domain.notification.service.NotificationHandleResult;
import com.seatrush.notificationconsumer.domain.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(NotificationEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    public NotificationEventConsumer(
            ObjectMapper objectMapper,
            NotificationService notificationService
    ) {
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
    }

    /**
     * 예매 완료 이벤트를 소비하여 사용자 알림을 발송합니다.
     */
    @KafkaListener(topics = KafkaTopic.RESERVATION_CONFIRMED)
    public void consumeReservationConfirmed(
            String payload,
            Acknowledgment acknowledgment
    ) throws JsonProcessingException {
        consume(payload, NotificationEventType.RESERVATION_CONFIRMED, acknowledgment);
    }

    /**
     * 결제 실패 이벤트를 소비하여 사용자 알림을 발송합니다.
     */
    @KafkaListener(topics = KafkaTopic.PAYMENT_FAILED)
    public void consumePaymentFailed(
            String payload,
            Acknowledgment acknowledgment
    ) throws JsonProcessingException {
        consume(payload, NotificationEventType.PAYMENT_FAILED, acknowledgment);
    }

    private void consume(
            String payload,
            NotificationEventType expectedType,
            Acknowledgment acknowledgment
    ) throws JsonProcessingException {
        NotificationEvent event =
                objectMapper.readValue(payload, NotificationEvent.class);
        NotificationHandleResult result =
                notificationService.handle(event, expectedType);

        acknowledgment.acknowledge();
        log.info(
                "Notification event consumed - eventId={}, type={}, result={}",
                event.eventId(),
                event.eventType(),
                result
        );
    }
}
