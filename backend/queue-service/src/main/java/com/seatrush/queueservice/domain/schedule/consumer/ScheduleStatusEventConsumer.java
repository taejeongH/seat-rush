package com.seatrush.queueservice.domain.schedule.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatrush.queueservice.common.kafka.KafkaTopic;
import com.seatrush.queueservice.domain.schedule.event.ScheduleStatusEvent;
import com.seatrush.queueservice.domain.schedule.repository.ScheduleStateRedisRepository;
import com.seatrush.queueservice.domain.schedule.repository.ScheduleStateSyncResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * 회차 상태 이벤트를 소비해 Queue Service의 Redis 상태를 갱신합니다.
 */
@Component
public class ScheduleStatusEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ScheduleStatusEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final ScheduleStateRedisRepository scheduleStateRepository;

    public ScheduleStatusEventConsumer(
            ObjectMapper objectMapper,
            ScheduleStateRedisRepository scheduleStateRepository
    ) {
        this.objectMapper = objectMapper;
        this.scheduleStateRepository = scheduleStateRepository;
    }

    /**
     * Redis 반영이 완료된 이후에만 Kafka offset을 커밋합니다.
     */
    @KafkaListener(topics = KafkaTopic.SCHEDULE_STATUS)
    public void consume(String payload, Acknowledgment acknowledgment) throws JsonProcessingException {
        ScheduleStatusEvent event = objectMapper.readValue(payload, ScheduleStatusEvent.class);
        ScheduleStateSyncResult result = scheduleStateRepository.synchronize(event);

        acknowledgment.acknowledge();
        log.info(
                "Schedule status event consumed - scheduleId={}, type={}, version={}, result={}",
                event.scheduleId(),
                event.eventType(),
                event.version(),
                result
        );
    }
}
