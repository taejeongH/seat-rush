package com.seatrush.ticketservice.domain.concert.event.publisher;

import com.seatrush.ticketservice.common.kafka.KafkaTopic;
import com.seatrush.ticketservice.domain.concert.entity.ConcertSchedule;
import com.seatrush.ticketservice.domain.concert.event.model.ScheduleEventType;
import com.seatrush.ticketservice.domain.concert.event.model.ScheduleStatusEvent;
import com.seatrush.ticketservice.domain.outbox.service.OutboxEventService;
import org.springframework.stereotype.Component;

/**
 * 회차 상태 이벤트를 Kafka에 직접 발행하지 않고 Outbox에 기록합니다.
 */
@Component
public class ScheduleEventOutboxWriter {

    private static final String AGGREGATE_TYPE = "CONCERT_SCHEDULE";

    private final OutboxEventService outboxEventService;

    public ScheduleEventOutboxWriter(OutboxEventService outboxEventService) {
        this.outboxEventService = outboxEventService;
    }

    public void append(ConcertSchedule schedule, ScheduleEventType eventType) {
        ScheduleStatusEvent event = ScheduleStatusEvent.from(schedule, eventType);

        outboxEventService.append(
                event.eventId(),
                AGGREGATE_TYPE,
                event.scheduleId(),
                event.eventType().name(),
                KafkaTopic.SCHEDULE_STATUS,
                event
        );
    }
}
