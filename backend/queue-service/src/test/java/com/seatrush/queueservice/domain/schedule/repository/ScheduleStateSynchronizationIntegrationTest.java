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
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ScheduleStateSynchronizationIntegrationTest {

    private static final Long SCHEDULE_ID = 9_999_998L;
    private static final ZoneId SCHEDULE_ZONE = ZoneId.of("Asia/Seoul");

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
     * 기존 문자열 시간 형식은 동일 version 이벤트로 epoch milliseconds 형식으로 변환합니다.
     */
    @Test
    void sameVersionMigratesLegacyTimeFormat() {
        String key = QueueKey.scheduleState(SCHEDULE_ID);
        redisTemplate.opsForHash().put(key, "status", ScheduleStatus.UPCOMING.name());
        redisTemplate.opsForHash().put(key, "bookingOpenAt", "2026-06-11T19:00:00");
        redisTemplate.opsForHash().put(key, "bookingCloseAt", "2026-06-11T20:00:00");
        redisTemplate.opsForHash().put(key, "version", "1");

        ScheduleStateSyncResult result = scheduleStateRepository.synchronize(
                createEvent(ScheduleStatus.UPCOMING, 1)
        );

        Object bookingOpenAt = redisTemplate.opsForHash().get(key, "bookingOpenAt");
        Object bookingCloseAt = redisTemplate.opsForHash().get(key, "bookingCloseAt");

        assertThat(result).isEqualTo(ScheduleStateSyncResult.APPLIED);
        assertThat(bookingOpenAt.toString()).containsOnlyDigits();
        assertThat(bookingCloseAt.toString()).containsOnlyDigits();
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

    /**
     * UPCOMING 상태라도 현재 시간이 예매 가능 구간이면 대기열 진입을 허용합니다.
     */
    @Test
    void upcomingScheduleIsAcceptedDuringBookingPeriod() {
        scheduleStateRepository.synchronize(createEvent(
                ScheduleStatus.UPCOMING,
                now().minusMinutes(1),
                now().plusHours(1),
                1
        ));

        assertThat(queueService.join(SCHEDULE_ID, 1L).position()).isEqualTo(1);
    }

    /**
     * Redis 서버 시각이 예매 시작 전이면 대기열 진입을 차단합니다.
     */
    @Test
    void scheduleIsRejectedBeforeBookingOpenTime() {
        scheduleStateRepository.synchronize(createEvent(
                ScheduleStatus.UPCOMING,
                now().plusHours(1),
                now().plusHours(2),
                1
        ));

        assertQueueNotOpen();
    }

    /**
     * Redis 서버 시각이 예매 종료 시각을 지났으면 대기열 진입을 차단합니다.
     */
    @Test
    void scheduleIsRejectedAfterBookingCloseTime() {
        scheduleStateRepository.synchronize(createEvent(
                ScheduleStatus.BOOKING_OPEN,
                now().minusHours(2),
                now().minusHours(1),
                1
        ));

        assertQueueNotOpen();
    }

    /**
     * 취소된 회차는 예매 가능 시간 안이어도 대기열 진입을 차단합니다.
     */
    @Test
    void canceledScheduleIsRejectedDuringBookingPeriod() {
        scheduleStateRepository.synchronize(createEvent(
                ScheduleStatus.CANCELED,
                now().minusMinutes(1),
                now().plusHours(1),
                1
        ));

        assertQueueNotOpen();
    }

    private ScheduleStatusEvent createEvent(ScheduleStatus status, long version) {
        return createEvent(
                status,
                now().minusMinutes(1),
                now().plusHours(1),
                version
        );
    }

    private ScheduleStatusEvent createEvent(
            ScheduleStatus status,
            LocalDateTime bookingOpenAt,
            LocalDateTime bookingCloseAt,
            long version
    ) {
        return new ScheduleStatusEvent(
                UUID.randomUUID(),
                ScheduleEventType.UPDATED,
                SCHEDULE_ID,
                status,
                bookingOpenAt,
                bookingCloseAt,
                version,
                Instant.now()
        );
    }

    private void assertQueueNotOpen() {
        assertThatThrownBy(() -> queueService.join(SCHEDULE_ID, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.QUEUE_NOT_OPEN);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(SCHEDULE_ZONE);
    }
}
