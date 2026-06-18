package com.seatrush.queueservice.domain.entrytoken.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatrush.queueservice.common.kafka.KafkaTopic;
import com.seatrush.queueservice.domain.entrytoken.event.EntrySlotReleaseEvent;
import com.seatrush.queueservice.domain.entrytoken.service.EntrySlotReleaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Ticket Service가 발행한 입장 슬롯 반환 이벤트를 소비합니다.
 *
 * 예매 완료, 실패, 취소, 만료 시 활성 입장 슬롯을 비워 다음 대기자가 들어올 수 있게 합니다.
 */
@Component
public class EntrySlotReleaseEventConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(EntrySlotReleaseEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final EntrySlotReleaseService entrySlotReleaseService;

    public EntrySlotReleaseEventConsumer(
            ObjectMapper objectMapper,
            EntrySlotReleaseService entrySlotReleaseService
    ) {
        this.objectMapper = objectMapper;
        this.entrySlotReleaseService = entrySlotReleaseService;
    }

    /**
     * entry-slot-release-v1 이벤트를 처리하고 성공 시 offset을 commit합니다.
     */
    @KafkaListener(topics = KafkaTopic.ENTRY_SLOT_RELEASE)
    public void consume(
            String payload,
            Acknowledgment acknowledgment
    ) throws JsonProcessingException {
        EntrySlotReleaseEvent event =
                objectMapper.readValue(payload, EntrySlotReleaseEvent.class);
        boolean released = entrySlotReleaseService.release(event);

        acknowledgment.acknowledge();
        log.info(
                "Entry slot release event consumed - eventId={}, reservationId={}, scheduleId={}, userId={}, reason={}, released={}",
                event.eventId(),
                event.reservationId(),
                event.scheduleId(),
                event.userId(),
                event.reason(),
                released
        );
    }
}
