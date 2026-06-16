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
     * Queue Service의 active entry slot을 반환한 뒤 Kafka offset을 커밋합니다.
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
