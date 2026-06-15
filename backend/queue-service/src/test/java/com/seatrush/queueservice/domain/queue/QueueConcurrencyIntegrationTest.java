package com.seatrush.queueservice.domain.queue;

import com.seatrush.queueservice.domain.queue.dto.response.QueueJoinResponseDto;
import com.seatrush.queueservice.domain.queue.service.QueueService;
import com.seatrush.queueservice.domain.schedule.event.ScheduleEventType;
import com.seatrush.queueservice.domain.schedule.event.ScheduleStatus;
import com.seatrush.queueservice.domain.schedule.event.ScheduleStatusEvent;
import com.seatrush.queueservice.domain.schedule.repository.ScheduleStateRedisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class QueueConcurrencyIntegrationTest {

    private static final Long SCHEDULE_ID = 9_999_999L;
    private static final ZoneId SCHEDULE_ZONE = ZoneId.of("Asia/Seoul");

    @Autowired
    private QueueService queueService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private ScheduleStateRedisRepository scheduleStateRepository;

    @BeforeEach
    void setUp() {
        scheduleStateRepository.synchronize(new ScheduleStatusEvent(
                UUID.randomUUID(),
                ScheduleEventType.SYNCHRONIZED,
                SCHEDULE_ID,
                ScheduleStatus.BOOKING_OPEN,
                LocalDateTime.now(SCHEDULE_ZONE).minusMinutes(1),
                LocalDateTime.now(SCHEDULE_ZONE).plusHours(1),
                1,
                Instant.now()
        ));
    }

    @AfterEach
    void cleanUp() {
        redisTemplate.delete(List.of(
                QueueKey.waiting(SCHEDULE_ID),
                QueueKey.sequence(SCHEDULE_ID),
                QueueKey.scheduleState(SCHEDULE_ID)
        ));
    }

    /**
     * 동시 진입한 사용자에게 중복 없는 순번이 발급되는지 검증합니다.
     */
    @Test
    void concurrentJoinAssignsUniquePositions() throws Exception {
        int userCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(20);

        try {
            List<Callable<QueueJoinResponseDto>> tasks = IntStream.rangeClosed(1, userCount)
                    .mapToObj(userId -> (Callable<QueueJoinResponseDto>)
                            () -> queueService.join(SCHEDULE_ID, (long) userId))
                    .toList();

            List<Future<QueueJoinResponseDto>> futures = executor.invokeAll(tasks);
            Set<Long> positions = new HashSet<>();

            for (Future<QueueJoinResponseDto> future : futures) {
                positions.add(future.get().position());
            }

            assertThat(positions).hasSize(userCount);
            assertThat(positions).containsExactlyInAnyOrderElementsOf(
                    IntStream.rangeClosed(1, userCount)
                            .mapToObj(Long::valueOf)
                            .toList()
            );
            assertThat(redisTemplate.opsForZSet().size(QueueKey.waiting(SCHEDULE_ID)))
                    .isEqualTo(userCount);
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 동일 사용자의 동시 요청이 대기열에 한 번만 등록되는지 검증합니다.
     */
    @Test
    void duplicateJoinRegistersOnce() throws Exception {
        int requestCount = 50;
        Long userId = 1L;
        ExecutorService executor = Executors.newFixedThreadPool(20);

        try {
            List<Callable<QueueJoinResponseDto>> tasks = IntStream.range(0, requestCount)
                    .mapToObj(index -> (Callable<QueueJoinResponseDto>)
                            () -> queueService.join(SCHEDULE_ID, userId))
                    .toList();

            List<Future<QueueJoinResponseDto>> futures = executor.invokeAll(tasks);
            int newJoinCount = 0;

            for (Future<QueueJoinResponseDto> future : futures) {
                QueueJoinResponseDto response = future.get();
                assertThat(response.position()).isEqualTo(1);

                if (!response.alreadyJoined()) {
                    newJoinCount++;
                }
            }

            assertThat(newJoinCount).isEqualTo(1);
            assertThat(redisTemplate.opsForZSet().size(QueueKey.waiting(SCHEDULE_ID)))
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }
}
