package com.seatrush.notificationconsumer.domain.notification.repository;

import com.seatrush.notificationconsumer.domain.notification.config.NotificationDeduplicationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Redisë¥??´ìš©??ë¹„ë™ê¸??Œë¦¼ ë©”ì‹œì§€??ì¤‘ë³µ ?Œë¹„(Deduplication)ë¥??ì?ìœ¼ë¡??ë³„ ë°??œì–´?˜ëŠ” ?ˆí¬ì§€? ë¦¬ ?´ë˜?¤ì…?ˆë‹¤.
 *
 * ë¶„ì‚° ?˜ê²½?ì„œ ?¬ëŸ¬ ì»¨ìŠˆë¨¸ê? ?™ì‹œ??ê°™ì? ?Œë¦¼ ?´ë²¤?¸ë? ë°›ì•„ ì¤‘ë³µ ?„ì†¡?˜ëŠ” ?í™©??ì°¨ë‹¨?©ë‹ˆ??
 */
@Repository
public class NotificationDeduplicationRepository {

    private static final String KEY_PREFIX = "notification:event:";
    private static final String PROCESSING = "PROCESSING";
    private static final String COMPLETED = "COMPLETED";

    private static final DefaultRedisScript<Long> CLAIM_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>();

    static {
        CLAIM_SCRIPT.setLocation(new ClassPathResource("scripts/claim_event.lua"));
        CLAIM_SCRIPT.setResultType(Long.class);

        RELEASE_SCRIPT.setLocation(new ClassPathResource("scripts/release_event.lua"));
        RELEASE_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redisTemplate;
    private final NotificationDeduplicationProperties properties;

    public NotificationDeduplicationRepository(
            StringRedisTemplate redisTemplate,
            NotificationDeduplicationProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /**
     * ?Œë¦¼ ?´ë²¤??ID???€??ì²˜ë¦¬ ê¶Œí•œ???ì?ìœ¼ë¡??”ì²­(Claim)?˜ê³  ?íƒœë¥??•ì¸?©ë‹ˆ??
     *
     * @param eventId ?Œë¦¼ ?´ë²¤??ê³ ìœ  UUID
     * @return ?íƒœ ê²€ì¦?ê²°ê³¼ (CLAIMED: ???ìœ  ?±ê³µ, COMPLETED: ?´ë? ?„ì†¡ ?„ë£Œ?? PROCESSING: ?€ ?¤ë ˆ??ì²˜ë¦¬ ì¤?
     * @throws IllegalStateException ?¤í¬ë¦½íŠ¸ ?¤í–‰ ê²°ê³¼ê°€ null??ê²½ìš°
     */
    public NotificationClaimResult claim(UUID eventId) {
        Long result = redisTemplate.execute(
                CLAIM_SCRIPT,
                List.of(key(eventId)),
                String.valueOf(properties.processingTtl().toMillis())
        );
        if (result == null) {
            throw new IllegalStateException("?Œë¦¼ ?´ë²¤??ì²˜ë¦¬ ?íƒœë¥??•ì¸?????†ìŠµ?ˆë‹¤.");
        }
        return switch (result.intValue()) {
            case 1 -> NotificationClaimResult.CLAIMED;
            case 2 -> NotificationClaimResult.COMPLETED;
            default -> NotificationClaimResult.PROCESSING;
        };
    }

    /**
     * ìµœì¢… ?Œë¦¼ ë°œì†¡???±ê³µ??ê²½ìš° ?´ë‹¹ ?´ë²¤???¤ì˜ ?íƒœë¥?COMPLETEDë¡?ë³€ê²½í•˜ê³?ë³´ì¡´ ê¸°ê°„(Ttl)??ì§€?•í•©?ˆë‹¤.
     *
     * @param eventId ?Œë¦¼ ?´ë²¤??ê³ ìœ  UUID
     */
    public void complete(UUID eventId) {
        redisTemplate.opsForValue().set(
                key(eventId),
                COMPLETED,
                properties.completedTtl()
        );
    }

    /**
     * ?Œë¦¼ ë°œì†¡ ?¤íŒ¨ ?±ì˜ ?´ìœ ë¡??ìœ  ê¶Œí•œ???¬ê¸°?´ì•¼ ????PROCESSING ???¤ë? ?? œ?©ë‹ˆ??
     *
     * @param eventId ?Œë¦¼ ?´ë²¤??ê³ ìœ  UUID
     */
    public void release(UUID eventId) {
        redisTemplate.execute(RELEASE_SCRIPT, List.of(key(eventId)));
    }

    /**
     * Redis???€?¥í•  ì¤‘ë³µ ?œì–´??ê³ ìœ  ??ëª…ì¹­??ë°˜í™˜?©ë‹ˆ??
     */
    private String key(UUID eventId) {
        return KEY_PREFIX + eventId;
    }
}
