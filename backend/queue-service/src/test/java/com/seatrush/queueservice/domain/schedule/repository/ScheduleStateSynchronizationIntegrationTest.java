package com.seatrush.queueservice.domain.schedule.repository;

import com.seatrush.queueservice.common.exception.CustomException;
import com.seatrush.queueservice.common.response.status.ErrorCode;
import com.seatrush.queueservice.domain.queue.QueueKey;
import com.seatrush.queueservice.domain.queue.service.QueueService;
import com.seatrush.queueservice.domain.schedule.event.ScheduleEventType;
import com.seatrush.queueservice.domain.schedule.event.ScheduleStatus;
import com.seatrush.queueservice.domain.schedule.event.ScheduleStatusEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ScheduleStateSynchronizationIntegrationTest {

    private static final Long SCHEDULE_ID = 9_999_998L;

    @Autowired
    private ScheduleStateRedisRepository scheduleStateRepository;

    @Autowired
    private QueueService queueService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @AfterEach
    void cleanUp() {
        redisTemplate.delete(List.of(
                QueueKey.waiting(SCHEDULE_ID),
                QueueKey.sequence(SCHEDULE_ID),
                QueueKey.scheduleState(SCHEDULE_ID)
        ));
    }

    /**
     * 현재 version보다 오래된 이벤트가 Redis 상태를 덮어쓰지 않는지 검증합니다.
     */
    @Test
    void staleEventIsIgnored() {
        ScheduleStateSyncResult applied = scheduleStateRepository.synchronize(
                createEvent(ScheduleStatus.BOOKING_OPEN, 2)
        );
        ScheduleStateSyncResult ignored = scheduleStateRepository.synchronize(
                createEvent(ScheduleStatus.CANCELED, 1)
        );

        Object status = redisTemplate.opsForHash()
                .get(QueueKey.scheduleState(SCHEDULE_ID), "status");

        assertThat(applied).isEqualTo(ScheduleStateSyncResult.APPLIED);
        assertThat(ignored).isEqualTo(ScheduleStateSyncResult.IGNORED);
        assertThat(status).isEqualTo(ScheduleStatus.BOOKING_OPEN.name());
    }

    /**
     * 동기화되지 않은 회차의 대기열 진입이 차단되는지 검증합니다.
     */
    @Test
    void missingScheduleIsRejected() {
        assertThatThrownBy(() -> queueService.join(SCHEDULE_ID, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SCHEDULE_NOT_FOUND);
    }

    /**
     * 대기열이 열리지 않은 회차의 진입이 차단되는지 검증합니다.
     */
    @Test
    void closedScheduleIsRejected() {
        scheduleStateRepository.synchronize(createEvent(ScheduleStatus.BOOKING_CLOSED, 1));

        assertThatThrownBy(() -> queueService.join(SCHEDULE_ID, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.QUEUE_NOT_OPEN);
    }

    private ScheduleStatusEvent createEvent(ScheduleStatus status, long version) {
        return new ScheduleStatusEvent(
                UUID.randomUUID(),
                ScheduleEventType.UPDATED,
                SCHEDULE_ID,
                status,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusHours(1),
                version,
                Instant.now()
        );
    }
}
