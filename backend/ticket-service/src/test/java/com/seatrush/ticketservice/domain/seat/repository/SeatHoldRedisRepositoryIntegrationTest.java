package com.seatrush.ticketservice.domain.seat.repository;

import com.seatrush.ticketservice.common.metrics.BusinessMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 Redis에서 좌석 선점 Lua Script의 원자성을 검증합니다.
 */
class SeatHoldRedisRepositoryIntegrationTest {

    private static final Long SCHEDULE_ID = 9_999_996L;
    private static final Long FIRST_SEAT_ID = 8_000_001L;
    private static final Long SECOND_SEAT_ID = 8_000_002L;

    private static LettuceConnectionFactory connectionFactory;
    private static RedisTemplate<String, String> redisTemplate;
    private static SeatHoldRedisRepository repository;

    private final List<String> holdIds = new ArrayList<>();

    @BeforeAll
    static void setUpRedis() {
        connectionFactory = new LettuceConnectionFactory("localhost", 6380);
        connectionFactory.afterPropertiesSet();

        StringRedisSerializer serializer = new StringRedisSerializer();
        redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.setKeySerializer(serializer);
        redisTemplate.setValueSerializer(serializer);
        redisTemplate.setHashKeySerializer(serializer);
        redisTemplate.setHashValueSerializer(serializer);
        redisTemplate.afterPropertiesSet();
        repository = new SeatHoldRedisRepository(
                redisTemplate,
                new BusinessMetrics(new SimpleMeterRegistry())
        );
    }

    @AfterEach
    void cleanUp() {
        redisTemplate.delete(List.of(
                SeatHoldKey.seat(SCHEDULE_ID, FIRST_SEAT_ID),
                SeatHoldKey.seat(SCHEDULE_ID, SECOND_SEAT_ID),
                SeatHoldKey.sectionIndex(SCHEDULE_ID, 1L, null)
        ));
        holdIds.stream()
                .map(SeatHoldKey::hold)
                .forEach(redisTemplate::delete);
    }

    @AfterAll
    static void closeRedis() {
        connectionFactory.destroy();
    }

    /**
     * 같은 좌석에 동시에 요청해도 정확히 하나의 hold만 성공합니다.
     */
    @Test
    void allowOnlyOneHoldForConcurrentRequestsToSameSeat() throws Exception {
        int requestCount = 20;
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(requestCount)) {
            List<Future<SeatHoldResult>> futures = IntStream.range(0, requestCount)
                    .mapToObj(index -> {
                        String holdId = "concurrent-hold-" + index;
                        holdIds.add(holdId);
                        return executor.submit(() -> {
                            ready.countDown();
                            start.await();
                            return repository.hold(
                                    hold(holdId, List.of(FIRST_SEAT_ID)),
                                    60_000
                            );
                        });
                    })
                    .toList();

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            long successCount = 0;
            for (Future<SeatHoldResult> future : futures) {
                if (future.get(5, TimeUnit.SECONDS).success()) {
                    successCount++;
                }
            }
            assertThat(successCount).isEqualTo(1);
        }
    }

    /**
     * 다중 좌석 중 하나가 이미 선점되어 있으면 나머지 좌석도 선점하지 않습니다.
     */
    @Test
    void leaveEverySeatUnchangedWhenMultiSeatHoldFails() {
        holdIds.addAll(List.of("existing-hold", "failed-hold"));
        assertThat(repository.hold(
                hold("existing-hold", List.of(SECOND_SEAT_ID)),
                60_000
        ).success()).isTrue();

        SeatHoldResult result = repository.hold(
                hold("failed-hold", List.of(FIRST_SEAT_ID, SECOND_SEAT_ID)),
                60_000
        );

        assertThat(result.success()).isFalse();
        assertThat(result.unavailableSeatId()).isEqualTo(SECOND_SEAT_ID);
        assertThat(redisTemplate.hasKey(SeatHoldKey.seat(SCHEDULE_ID, FIRST_SEAT_ID))).isFalse();
    }

    /**
     * 유효한 hold는 예매 결제 기한까지 TTL만 연장하고 reservationId를 저장하지 않습니다.
     */
    @Test
    void extendHoldTtlWhenBindingReservation() {
        String holdId = "reservation-hold";
        holdIds.add(holdId);
        SeatHold hold = hold(holdId, List.of(FIRST_SEAT_ID));
        assertThat(repository.hold(hold, 5_000).success()).isTrue();

        boolean extended = repository.extendForReservation(
                hold,
                60_000,
                Instant.now().plusSeconds(60)
        );

        assertThat(extended).isTrue();
        assertThat(redisTemplate.opsForHash()
                .hasKey(SeatHoldKey.hold(holdId), "reservationId"))
                .isFalse();
        assertThat(redisTemplate.getExpire(SeatHoldKey.seat(SCHEDULE_ID, FIRST_SEAT_ID)))
                .isBetween(55L, 60L);
        assertThat(redisTemplate.opsForZSet().score(
                SeatHoldKey.sectionIndex(SCHEDULE_ID, 1L, null),
                FIRST_SEAT_ID.toString()
        )).isGreaterThan(Instant.now().plusSeconds(55).toEpochMilli() * 1.0);
    }

    /**
     * 구역별 hold 인덱스는 현재 선점된 좌석만 반환하고 해제된 좌석은 즉시 제거합니다.
     */
    @Test
    void returnOnlyHeldSeatsFromSectionIndexAndRemoveReleasedSeats() {
        String holdId = "indexed-hold";
        holdIds.add(holdId);
        SeatHold hold = hold(holdId, List.of(FIRST_SEAT_ID));
        assertThat(repository.hold(hold, 60_000).success()).isTrue();

        assertThat(repository.findHeldSeats(
                SCHEDULE_ID,
                1L,
                List.of(FIRST_SEAT_ID, SECOND_SEAT_ID),
                null
        )).containsEntry(FIRST_SEAT_ID, true)
                .containsEntry(SECOND_SEAT_ID, false);

        assertThat(repository.release(hold)).isTrue();
        assertThat(repository.findHeldSeats(
                SCHEDULE_ID,
                1L,
                List.of(FIRST_SEAT_ID, SECOND_SEAT_ID),
                null
        )).containsEntry(FIRST_SEAT_ID, false)
                .containsEntry(SECOND_SEAT_ID, false);
    }

    /**
     * 연습 세션의 hold 인덱스는 실제 예매 영역과 분리됩니다.
     */
    @Test
    void isolatePracticeSectionIndexFromRealReservationIndex() {
        String practiceSessionId = "practice-index-test";
        SeatHold practiceHold = new SeatHold(
                "practice-indexed-hold",
                SCHEDULE_ID,
                100L,
                "entry-token-jti",
                practiceSessionId,
                List.of(FIRST_SEAT_ID),
                java.util.Map.of(FIRST_SEAT_ID, 1L),
                Instant.now().plusSeconds(60)
        );

        assertThat(repository.hold(practiceHold, 60_000).success()).isTrue();
        assertThat(repository.findHeldSeats(
                SCHEDULE_ID,
                1L,
                List.of(FIRST_SEAT_ID),
                practiceSessionId
        )).containsEntry(FIRST_SEAT_ID, true);
        assertThat(repository.findHeldSeats(
                SCHEDULE_ID,
                1L,
                List.of(FIRST_SEAT_ID),
                null
        )).containsEntry(FIRST_SEAT_ID, false);

        assertThat(repository.release(practiceHold)).isTrue();
    }

    /**
     * 좌석 TTL 만료 뒤 인덱스에 남은 항목은 조회 시 함께 정리합니다.
     */
    @Test
    void removeExpiredSeatsFromSectionIndexWhenReading() {
        String indexKey = SeatHoldKey.sectionIndex(SCHEDULE_ID, 1L, null);
        redisTemplate.opsForZSet().add(
                indexKey,
                FIRST_SEAT_ID.toString(),
                Instant.now().minusSeconds(1).toEpochMilli()
        );

        assertThat(repository.findHeldSeats(
                SCHEDULE_ID,
                1L,
                List.of(FIRST_SEAT_ID),
                null
        )).containsEntry(FIRST_SEAT_ID, false);
        assertThat(redisTemplate.hasKey(indexKey)).isFalse();
    }

    private SeatHold hold(String holdId, List<Long> seatIds) {
        return new SeatHold(
                holdId,
                SCHEDULE_ID,
                100L,
                "entry-token-jti",
                null,
                seatIds,
                seatIds.stream().collect(java.util.stream.Collectors.toMap(
                        seatId -> seatId,
                        seatId -> 1L
                )),
                Instant.now().plusSeconds(60)
        );
    }
}
