package com.seatrush.ticketservice.domain.reservation.event.publisher;

import com.seatrush.ticketservice.common.kafka.KafkaTopic;
import com.seatrush.ticketservice.domain.outbox.service.OutboxEventService;
import com.seatrush.ticketservice.domain.reservation.entity.Reservation;
import com.seatrush.ticketservice.domain.reservation.event.model.PaymentRequestEvent;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class PaymentRequestOutboxWriter {

    private static final String AGGREGATE_TYPE = "RESERVATION";
    private static final String EVENT_TYPE = "PAYMENT_REQUESTED";

    private final OutboxEventService outboxEventService;

    public PaymentRequestOutboxWriter(OutboxEventService outboxEventService) {
        this.outboxEventService = outboxEventService;
    }

    /**
     * 결제 요청 이벤트를 예매 상태 변경과 같은 트랜잭션의 Outbox에 저장합니다.
     */
    public void append(Reservation reservation, LocalDateTime requestedAt) {
        PaymentRequestEvent event = PaymentRequestEvent.from(reservation, requestedAt);
        outboxEventService.append(
                event.eventId(),
                AGGREGATE_TYPE,
                reservation.getId(),
                EVENT_TYPE,
                KafkaTopic.PAYMENT_REQUEST,
                event
        );
    }
}
