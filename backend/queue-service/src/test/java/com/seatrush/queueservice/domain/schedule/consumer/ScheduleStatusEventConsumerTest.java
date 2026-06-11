package com.seatrush.queueservice.domain.schedule.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.seatrush.queueservice.domain.schedule.event.ScheduleEventType;
import com.seatrush.queueservice.domain.schedule.event.ScheduleStatus;
import com.seatrush.queueservice.domain.schedule.event.ScheduleStatusEvent;
import com.seatrush.queueservice.domain.schedule.repository.ScheduleStateRedisRepository;
import com.seatrush.queueservice.domain.schedule.repository.ScheduleStateSyncResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleStatusEventConsumerTest {

    @Mock
    private ScheduleStateRedisRepository scheduleStateRepository;

    @Mock
    private Acknowledgment acknowledgment;

    private ObjectMapper objectMapper;
    private ScheduleStatusEventConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        consumer = new ScheduleStatusEventConsumer(objectMapper, scheduleStateRepository);
    }

    /**
     * Redis 반영이 성공하면 Kafka offset을 커밋하는지 검증합니다.
     */
    @Test
    void successfulSyncAcknowledgesEvent() throws Exception {
        ScheduleStatusEvent event = createEvent();
        when(scheduleStateRepository.synchronize(event))
                .thenReturn(ScheduleStateSyncResult.APPLIED);

        consumer.consume(objectMapper.writeValueAsString(event), acknowledgment);

        verify(acknowledgment).acknowledge();
    }

    /**
     * Redis 반영이 실패하면 Kafka offset을 커밋하지 않는지 검증합니다.
     */
    @Test
    void failedSyncDoesNotAcknowledgeEvent() throws Exception {
        ScheduleStatusEvent event = createEvent();
        doThrow(new IllegalStateException("Redis unavailable"))
                .when(scheduleStateRepository)
                .synchronize(event);

        assertThatThrownBy(() ->
                consumer.consume(objectMapper.writeValueAsString(event), acknowledgment)
        ).isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(acknowledgment);
    }

    private ScheduleStatusEvent createEvent() {
        return new ScheduleStatusEvent(
                UUID.randomUUID(),
                ScheduleEventType.UPDATED,
                1L,
                ScheduleStatus.BOOKING_OPEN,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusHours(1),
                1,
                Instant.now()
        );
    }
}
