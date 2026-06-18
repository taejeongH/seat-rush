package com.seatrush.notificationconsumer.domain.notification.service;

import com.seatrush.notificationconsumer.domain.event.model.NotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 실제 외부 알림 대신 로그로 발송 결과를 남기는 Mock sender입니다.
 */
@Component
public class MockNotificationSender {

    private static final Logger log =
            LoggerFactory.getLogger(MockNotificationSender.class);

    /**
     * 알림 이벤트 내용을 로그로 출력합니다.
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
