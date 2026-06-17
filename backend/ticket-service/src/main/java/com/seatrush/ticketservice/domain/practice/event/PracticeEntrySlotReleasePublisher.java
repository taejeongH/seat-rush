package com.seatrush.ticketservice.domain.practice.event;

import com.seatrush.ticketservice.common.kafka.KafkaTopic;
import com.seatrush.ticketservice.domain.practice.repository.PracticeReservationState;
import com.seatrush.ticketservice.domain.reservation.event.model.EntrySlotReleaseEvent;
import com.seatrush.ticketservice.domain.reservation.event.model.EntrySlotReleaseReason;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class PracticeEntrySlotReleasePublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PracticeEntrySlotReleasePublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(
            PracticeReservationState state,
            EntrySlotReleaseReason reason
    ) {
        EntrySlotReleaseEvent event = new EntrySlotReleaseEvent(
                UUID.randomUUID(),
                state.reservationId(),
                state.scheduleId(),
                state.userId(),
                state.entryTokenId(),
                reason,
                LocalDateTime.now(),
                state.practiceSessionId()
        );
        kafkaTemplate.send(
                KafkaTopic.ENTRY_SLOT_RELEASE,
                state.scheduleId().toString(),
                event
        );
    }
}
