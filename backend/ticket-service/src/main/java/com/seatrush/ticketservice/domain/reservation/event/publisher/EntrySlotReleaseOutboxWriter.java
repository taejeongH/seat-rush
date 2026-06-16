package com.seatrush.ticketservice.domain.reservation.event.publisher;

import com.seatrush.ticketservice.common.kafka.KafkaTopic;
import com.seatrush.ticketservice.domain.outbox.service.OutboxEventService;
import com.seatrush.ticketservice.domain.reservation.entity.Reservation;
import com.seatrush.ticketservice.domain.reservation.event.model.EntrySlotReleaseEvent;
import com.seatrush.ticketservice.domain.reservation.event.model.EntrySlotReleaseReason;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class EntrySlotReleaseOutboxWriter {

    private static final String AGGREGATE_TYPE = "RESERVATION";
    private static final String EVENT_TYPE = "ENTRY_SLOT_RELEASED";

    private final OutboxEventService outboxEventService;

    public EntrySlotReleaseOutboxWriter(OutboxEventService outboxEventService) {
        this.outboxEventService = outboxEventService;
    }

    /**
     * entry slot 반환 이벤트를 예매 상태 변경과 같은 트랜잭션의 Outbox에 저장합니다.
     */
    public void append(
            Reservation reservation,
            EntrySlotReleaseReason reason,
            LocalDateTime occurredAt
    ) {
        if (reservation.getEntryTokenId() == null
                || reservation.getEntryTokenId().isBlank()) {
            return;
        }

        EntrySlotReleaseEvent event =
                EntrySlotReleaseEvent.from(reservation, reason, occurredAt);
        outboxEventService.append(
                event.eventId(),
                AGGREGATE_TYPE,
                reservation.getId(),
                EVENT_TYPE,
                KafkaTopic.ENTRY_SLOT_RELEASE,
                event
        );
    }
}
