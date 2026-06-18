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
 * Ticket Service의 회차 상태 이벤트를 소비해 Redis 회차 상태를 동기화합니다.
 *
 * Queue Service는 Ticket Service DB를 직접 조회하지 않고 이벤트로 전달받은 상태만 사용합니다.
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
     * schedule-status-v1 이벤트를 Redis Hash에 반영하고 처리가 끝난 뒤 offset을 commit합니다.
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
