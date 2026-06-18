package com.seatrush.notificationconsumer.domain.notification.service;

import com.seatrush.notificationconsumer.domain.event.model.NotificationEvent;
import com.seatrush.notificationconsumer.domain.event.model.NotificationEventType;
import com.seatrush.notificationconsumer.domain.notification.repository.NotificationClaimResult;
import com.seatrush.notificationconsumer.domain.notification.repository.NotificationDeduplicationRepository;
import org.springframework.stereotype.Service;

/**
 * ?Œë¦¼ ?˜ì‹  ?´ë²¤?¸ë? ê²€ì¦í•˜ê³? ì¤‘ë³µ ë°œì†¡??ë°©ì??˜ë©° ?Œë¦¼??ìµœì¢… ?„ì†¡?˜ëŠ” ë¹„ì¦ˆ?ˆìŠ¤ ?œë¹„???´ë˜?¤ì…?ˆë‹¤.
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
     * ?Œë¦¼ ?´ë²¤?¸ë? ê°€ê³µí•˜???„ì†¡?˜ë©°, Redis ë¶„ì‚° ??Deduplication)???¬ìš©?˜ì—¬ ì¤‘ë³µ ë°œì†¡??ë°©ì??©ë‹ˆ??
     *
     * 1. ?Œë¦¼ ?´ë²¤??ê°ì²´??? íš¨?±ê³¼ ?€?…ì„ 1ì°?ê²€ì¦í•©?ˆë‹¤.
     * 2. Redis???´ë²¤??IDë¥??±ë¡(claim)?˜ì—¬ ?´ë? ì²˜ë¦¬ ?„ë£Œ?˜ì—ˆ?”ì? ?¹ì? ì²˜ë¦¬ ì¤‘ì¸ì§€ ?€ì¡°í•©?ˆë‹¤.
     * 3. ? ê·œ??ê²½ìš° ?Œë¦¼ ?„ì†¡??ì²˜ë¦¬?????„ë£Œ(complete) ?íƒœë¡?ê¸°ë¡?©ë‹ˆ??
     * 4. ë§Œì•½ ?„ì†¡ ì¤??°í????ˆì™¸ê°€ ë°œìƒ?˜ë©´ ?½ì„ ?´ì œ(release)?˜ì—¬ ë¦¬ìŠ¤?ˆê? ?¬ì‹œ?„í•  ???ˆë„ë¡?ì§€?í•©?ˆë‹¤.
     *
     * @param event Kafkaë¡œë???? ì…???Œë¦¼ ?´ë²¤??ê°ì²´
     * @param expectedType ?•ìƒ?ìœ¼ë¡?ì²˜ë¦¬?´ì•¼ ?˜ëŠ” ?€???Œë¦¼ ?´ë²¤???€??
     * @return ?Œë¦¼ ?„ì†¡ ê²°ê³¼ ?íƒœ (SENT: ?„ì†¡ ?±ê³µ, DUPLICATE: ì¤‘ë³µ ì²˜ë¦¬ ì°¨ë‹¨??
     * @throws IllegalStateException ?´ë? ?™ì¼ ?Œë¦¼ê±´ì´ ì²˜ë¦¬ ì¤‘ì¸ ê²½ìš° ë°œìƒ
     * @throws IllegalArgumentException ?Œë¦¼ ?°ì´?°ê? ? íš¨?˜ì? ?Šê±°???€?…ì´ ë¶ˆì¼ì¹˜í•˜??ê²½ìš° ë°œìƒ
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
            throw new IllegalStateException("?Œë¦¼ ?´ë²¤?¸ê? ?´ë? ì²˜ë¦¬ ì¤‘ì…?ˆë‹¤.");
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
     * ?Œë¦¼ ?´ë²¤???•ë³´???„ìˆ˜ ?•í•©??ë°??•ìƒ ?€???¬ë?ë¥?ê²€ì¦í•©?ˆë‹¤.
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
            throw new IllegalArgumentException("?Œë¦¼ ?´ë²¤???•ì‹???¬ë°”ë¥´ì? ?ŠìŠµ?ˆë‹¤.");
        }
    }
}
