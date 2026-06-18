package com.seatrush.queueservice.domain.entrytoken.service;

import com.seatrush.queueservice.common.exception.CustomException;
import com.seatrush.queueservice.common.response.status.ErrorCode;
import com.seatrush.queueservice.domain.entrytoken.event.EntrySlotReleaseEvent;
import com.seatrush.queueservice.domain.entrytoken.repository.EntryTokenRedisRepository;
import org.springframework.stereotype.Service;

/**
 * ?ˆë§¤ ìµœì¢… ê²°ê³¼(?±ê³µ/?¤íŒ¨/?œê°„ë§Œë£Œ ?????°ë¼ ?œì„± ?ˆë§¤ ê°€???…ì¥ ?¬ë¡¯???Œìˆ˜ ë°?ë³µêµ¬?˜ëŠ” ë¹„ì¦ˆ?ˆìŠ¤ ?œë¹„???´ë˜?¤ì…?ˆë‹¤.
 */
@Service
public class EntrySlotReleaseService {

    private final EntryTokenRedisRepository entryTokenRedisRepository;

    public EntrySlotReleaseService(EntryTokenRedisRepository entryTokenRedisRepository) {
        this.entryTokenRedisRepository = entryTokenRedisRepository;
    }

    /**
     * ?˜ì‹ ???ˆë§¤ ê²°ê³¼ ?´ë²¤???°ì´?°ë? ë°”íƒ•?¼ë¡œ, ?´ë‹¹ ?¬ìš©?ì˜ ?…ì¥ ?œí•œ ?œê°„ ?¬ë¡¯ ?ìœ  ?°ì´?°ë? Redis?ì„œ ë©±ë“±?˜ê²Œ ?´ì œ?©ë‹ˆ??
     *
     * 1. ?´ì œ ?´ë²¤???•ë³´(scheduleId, userId, entryTokenId ?????„ìˆ˜ ?•í•©?±ì„ ê²€ì¦í•©?ˆë‹¤.
     * 2. Redis `activeEntries` ì§‘í•©?ì„œ ?´ë‹¹ ?¬ìš©?ì˜ ?…ì¥ê¶Œì„ ë§Œë£Œ ì²˜ë¦¬?˜ì—¬ ?¤ë¥¸ ?€ê¸°ì—´ ?¬ìš©?ê? ?…ì¥?????ˆë„ë¡??¬ë¡¯ ?¬ìœ ë¶„ì„ ?œê³µ?©ë‹ˆ??
     *
     * @param event ?ˆë§¤ ?„ë¡œ?¸ìŠ¤ ì¢…ë£Œ ë°??¬ë¡¯ ë°˜í™˜ ?¬ìœ ë¥??´ì? ?´ë²¤???•ë³´
     * @return ?±ê³µ?ìœ¼ë¡?ë°˜í™˜ ì²˜ë¦¬ê°€ ?„ë£Œ??ê²½ìš° true, ê·¸ë ‡ì§€ ?Šì? ê²½ìš° false
     */
    public boolean release(EntrySlotReleaseEvent event) {
        validate(event);
        return entryTokenRedisRepository.releaseSlot(
                event.scheduleId(),
                event.userId(),
                event.entryTokenId(),
                event.practiceSessionId()
        );
    }

    private void validate(EntrySlotReleaseEvent event) {
        if (event == null
                || event.eventId() == null
                || event.reservationId() == null
                || event.scheduleId() == null
                || event.userId() == null
                || event.entryTokenId() == null
                || event.entryTokenId().isBlank()
                || event.reason() == null
                || event.occurredAt() == null) {
            throw new CustomException(ErrorCode.INVALID_ENTRY_SLOT_RELEASE_EVENT);
        }
    }
}
