package com.seatrush.ticketservice.domain.concert.event.publisher;

import com.seatrush.ticketservice.common.kafka.KafkaTopic;
import com.seatrush.ticketservice.domain.concert.entity.ConcertSchedule;
import com.seatrush.ticketservice.domain.concert.event.model.ScheduleEventType;
import com.seatrush.ticketservice.domain.concert.event.model.ScheduleStatusEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * 회차 상태 변경 이벤트를 Kafka에 발행합니다.
 */
@Component
public class ScheduleEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ScheduleEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public ScheduleEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public CompletableFuture<Void> publish(
            ConcertSchedule schedule,
            ScheduleEventType eventType
    ) {
        ScheduleStatusEvent event = ScheduleStatusEvent.from(schedule, eventType);

        return kafkaTemplate
                .send(KafkaTopic.SCHEDULE_STATUS, schedule.getId().toString(), event)
                .thenAccept(result -> log.info(
                        "Schedule status event published - scheduleId={}, type={}, version={}, partition={}, offset={}",
                        event.scheduleId(),
                        event.eventType(),
                        event.version(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset()
                ));
    }
}
