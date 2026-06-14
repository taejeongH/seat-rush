package com.seatrush.notificationconsumer.domain.notification.service;

import com.seatrush.notificationconsumer.domain.event.model.NotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MockNotificationSender {

    private static final Logger log =
            LoggerFactory.getLogger(MockNotificationSender.class);

    /**
     * 실제 외부 알림 채널 대신 발송 내용을 로그로 기록합니다.
     */
    public void send(NotificationEvent event) {
        log.info(
                "Mock notification sent - eventId={}, type={}, userId={}, email={}, reservationId={}, paymentId={}",
                event.eventId(),
                event.eventType(),
                event.userId(),
                event.email(),
                event.reservationId(),
                event.paymentId()
        );
    }
}
