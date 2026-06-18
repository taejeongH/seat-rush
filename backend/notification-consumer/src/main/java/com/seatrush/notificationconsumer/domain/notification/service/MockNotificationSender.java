package com.seatrush.notificationconsumer.domain.notification.service;

import com.seatrush.notificationconsumer.domain.event.model.NotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ?¸ë? ?Œë¦¼ API(?? ?Œë¦¼?? SMS, SMTP ?? ?°ë™??ëª¨í‚¹?˜ì—¬ ë°œì†¡ ?´ì—­??ë¡œê¹…?˜ëŠ” ?„ì†¡ ëª¨í‚¹ ?´ë˜?¤ì…?ˆë‹¤.
 */
@Component
public class MockNotificationSender {

    private static final Logger log =
            LoggerFactory.getLogger(MockNotificationSender.class);

    /**
     * ?¸ë? ì±„ë„???µí•œ ?¤ì œ ?Œë¦¼ ?„ì†¡ ?€?? ?„ë‹¬???Œë¦¼ ?´ë²¤?¸ì˜ ?µì‹¬ ë³¸ë¬¸ ?•ë³´ë¥??•í˜•?”ëœ ?¬ë§·?¼ë¡œ ë¡œê¹…?©ë‹ˆ??
     *
     * @param event ?„ì†¡???Œë¦¼ ?ì„¸ ?•ë³´ ê°ì²´
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
