package com.seatrush.queueservice.domain.entrytoken.service;

import com.seatrush.queueservice.common.exception.CustomException;
import com.seatrush.queueservice.common.response.status.ErrorCode;
import com.seatrush.queueservice.domain.entrytoken.event.EntrySlotReleaseEvent;
import com.seatrush.queueservice.domain.entrytoken.repository.EntryTokenRedisRepository;
import org.springframework.stereotype.Service;

/**
 * Ticket Service에서 발행한 입장 슬롯 반환 이벤트를 처리합니다.
 */
@Service
public class EntrySlotReleaseService {

    private final EntryTokenRedisRepository entryTokenRedisRepository;

    public EntrySlotReleaseService(EntryTokenRedisRepository entryTokenRedisRepository) {
        this.entryTokenRedisRepository = entryTokenRedisRepository;
    }

    /**
     * 예매 완료, 실패, 취소, 만료 이벤트를 기반으로 활성 입장 슬롯을 반환합니다.
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
