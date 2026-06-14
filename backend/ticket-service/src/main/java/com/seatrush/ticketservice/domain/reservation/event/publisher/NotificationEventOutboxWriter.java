package com.seatrush.ticketservice.domain.reservation.event.publisher;

import com.seatrush.ticketservice.common.kafka.KafkaTopic;
import com.seatrush.ticketservice.domain.outbox.service.OutboxEventService;
import com.seatrush.ticketservice.domain.reservation.entity.Reservation;
import com.seatrush.ticketservice.domain.reservation.event.model.NotificationEvent;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class NotificationEventOutboxWriter {

    private static final String AGGREGATE_TYPE = "RESERVATION";

    private final OutboxEventService outboxEventService;

    public NotificationEventOutboxWriter(OutboxEventService outboxEventService) {
        this.outboxEventService = outboxEventService;
    }

    /**
     * 예매 완료 알림 이벤트를 예매 상태 변경과 같은 트랜잭션에 저장합니다.
     */
    public void appendReservationConfirmed(
            Reservation reservation,
            LocalDateTime occurredAt
    ) {
        NotificationEvent event =
                NotificationEvent.reservationConfirmed(reservation, occurredAt);
        append(reservation, event, KafkaTopic.RESERVATION_CONFIRMED);
    }

    /**
     * 결제 실패 알림 이벤트를 예매 상태 변경과 같은 트랜잭션에 저장합니다.
     */
    public void appendPaymentFailed(
            Reservation reservation,
            LocalDateTime occurredAt
    ) {
        NotificationEvent event =
                NotificationEvent.paymentFailed(reservation, occurredAt);
        append(reservation, event, KafkaTopic.PAYMENT_FAILED);
    }

    private void append(
            Reservation reservation,
            NotificationEvent event,
            String topic
    ) {
        outboxEventService.append(
                event.eventId(),
                AGGREGATE_TYPE,
                reservation.getId(),
                event.eventType().name(),
                topic,
                event
        );
    }
}
