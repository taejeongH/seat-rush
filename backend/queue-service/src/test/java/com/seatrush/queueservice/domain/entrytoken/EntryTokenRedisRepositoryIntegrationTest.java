package com.seatrush.queueservice.domain.entrytoken;

import com.seatrush.queueservice.domain.entrytoken.repository.EntryTokenIssueResult;
import com.seatrush.queueservice.domain.entrytoken.repository.EntryTokenIssueStatus;
import com.seatrush.queueservice.domain.entrytoken.repository.EntryTokenRedisRepository;
import com.seatrush.queueservice.domain.queue.QueueKey;
import com.seatrush.queueservice.domain.queue.QueueStatus;
import com.seatrush.queueservice.domain.queue.dto.response.QueuePositionResponseDto;
import com.seatrush.queueservice.domain.queue.repository.QueueAdmissionState;
import com.seatrush.queueservice.domain.queue.repository.QueueRedisRepository;
import com.seatrush.queueservice.domain.queue.service.QueueService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EntryTokenRedisRepositoryIntegrationTest {

    private static final Long SCHEDULE_ID = 9_999_997L;
    private static final Long FIRST_USER_ID = 101L;
    private static final Long SECOND_USER_ID = 102L;

    @Autowired
    private EntryTokenRedisRepository entryTokenRedisRepository;

    @Autowired
    private QueueRedisRepository queueRedisRepository;

    @Autowired
    private QueueService queueService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private final List<String> issuedTokens = new ArrayList<>();
    private final List<Long> testUserIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        long now = Instant.now().toEpochMilli();
        String scheduleKey = QueueKey.scheduleState(SCHEDULE_ID);

        redisTemplate.opsForHash().put(scheduleKey, "status", "BOOKING_OPEN");
        redisTemplate.opsForHash().put(scheduleKey, "bookingOpenAt", Long.toString(now - 60_000));
        redisTemplate.opsForHash().put(scheduleKey, "bookingCloseAt", Long.toString(now + 60_000));
        redisTemplate.opsForZSet().add(QueueKey.waiting(SCHEDULE_ID), FIRST_USER_ID.toString(), 1);
        redisTemplate.opsForZSet().add(QueueKey.waiting(SCHEDULE_ID), SECOND_USER_ID.toString(), 2);
        testUserIds.add(FIRST_USER_ID);
        testUserIds.add(SECOND_USER_ID);
    }

    @AfterEach
    void cleanUp() {
        List<String> keys = new ArrayList<>(List.of(
                QueueKey.waiting(SCHEDULE_ID),
                QueueKey.sequence(SCHEDULE_ID),
                QueueKey.scheduleState(SCHEDULE_ID),
                QueueKey.activeEntries(SCHEDULE_ID),
                QueueKey.sessionExpirations(SCHEDULE_ID)
        ));
        testUserIds.stream()
                .map(userId -> EntryTokenKey.userToken(SCHEDULE_ID, userId))
                .forEach(keys::add);
        testUserIds.stream()
                .map(userId -> QueueKey.session(SCHEDULE_ID, userId))
                .forEach(keys::add);
        redisTemplate.delete(keys);
    }

    /**
     * 입장 가능한 첫 사용자에게 entryToken을 발급하는지 확인합니다.
     */
    @Test
    void eligibleUserReceivesValidEntryToken() {
        String token = "first-entry-token";
        issuedTokens.add(token);

        EntryTokenIssueResult result = issue(FIRST_USER_ID, token, 1, 60_000);

        assertThat(result.status()).isEqualTo(EntryTokenIssueStatus.ISSUED);
        assertThat(result.entryToken()).isEqualTo(token);
    }

    /**
     * 동일 사용자의 재요청에는 새 토큰 대신 기존 토큰을 반환하는지 확인합니다.
     */
    @Test
    void repeatedIssueReturnsExistingEntryToken() {
        String firstToken = "existing-entry-token";
        String secondToken = "unused-entry-token";
        issuedTokens.add(firstToken);
        issuedTokens.add(secondToken);

        EntryTokenIssueResult first = issue(FIRST_USER_ID, firstToken, 1, 60_000);
        EntryTokenIssueResult repeated = issue(FIRST_USER_ID, secondToken, 1, 60_000);

        assertThat(first.status()).isEqualTo(EntryTokenIssueStatus.ISSUED);
        assertThat(repeated.status()).isEqualTo(EntryTokenIssueStatus.ALREADY_ISSUED);
        assertThat(repeated.entryToken()).isEqualTo(firstToken);
    }

    /**
     * 활성 입장 인원이 가득 차면 다음 사용자의 토큰 발급을 제한하는지 확인합니다.
     */
    @Test
    void admissionCapacityLimitsEntryTokenIssuance() {
        String firstToken = "capacity-first-token";
        String secondToken = "capacity-second-token";
        issuedTokens.add(firstToken);
        issuedTokens.add(secondToken);

        EntryTokenIssueResult first = issue(FIRST_USER_ID, firstToken, 1, 60_000);
        EntryTokenIssueResult second = issue(SECOND_USER_ID, secondToken, 1, 60_000);

        assertThat(first.status()).isEqualTo(EntryTokenIssueStatus.ISSUED);
        assertThat(second.status()).isEqualTo(EntryTokenIssueStatus.ENTRY_NOT_ALLOWED);
    }

    /**
     * 활성 입장 슬롯이 남아 있으면 대기열 선두 사용자를 입장 가능 상태로 반환하는지 확인합니다.
     */
    @Test
    void firstWaitingUserIsEnterableWhenSlotIsAvailable() {
        QueueAdmissionState state = queueRedisRepository.getAdmissionState(
                SCHEDULE_ID,
                FIRST_USER_ID,
                1
        );

        assertThat(state.position()).isEqualTo(1);
        assertThat(state.totalWaiting()).isEqualTo(2);
        assertThat(state.enterable()).isTrue();
    }

    /**
     * 순번 조회는 사용자 세션 TTL을 갱신하지 않고 heartbeat만 갱신하는지 확인합니다.
     */
    @Test
    void queuePositionDoesNotRefreshUserSession() {
        long expiresAt = Instant.now().plusSeconds(20).toEpochMilli();
        redisTemplate.opsForZSet().add(
                QueueKey.sessionExpirations(SCHEDULE_ID),
                FIRST_USER_ID.toString(),
                expiresAt
        );

        queueRedisRepository.getAdmissionState(SCHEDULE_ID, FIRST_USER_ID, 1);

        Double storedExpiresAt = redisTemplate.opsForZSet().score(
                QueueKey.sessionExpirations(SCHEDULE_ID),
                FIRST_USER_ID.toString()
        );
        assertThat(storedExpiresAt).isEqualTo((double) expiresAt);
    }

    /**
     * 순번 조회 응답이 입장 가능한 사용자에게 ENTERABLE 상태를 제공하는지 확인합니다.
     */
    @Test
    void queuePositionResponseReturnsEnterableStatus() {
        QueuePositionResponseDto response = queueService.getMyPosition(SCHEDULE_ID, FIRST_USER_ID);

        assertThat(response.position()).isEqualTo(1);
        assertThat(response.status()).isEqualTo(QueueStatus.ENTERABLE);
    }

    /**
     * 활성 입장 슬롯이 가득 차면 다음 대기 사용자를 대기 상태로 반환하는지 확인합니다.
     */
    @Test
    void nextWaitingUserRemainsWaitingWhenCapacityIsFull() {
        String token = "full-capacity-token";
        issuedTokens.add(token);
        issue(FIRST_USER_ID, token, 1, 60_000);

        QueueAdmissionState state = queueRedisRepository.getAdmissionState(
                SCHEDULE_ID,
                SECOND_USER_ID,
                1
        );

        assertThat(state.position()).isEqualTo(1);
        assertThat(state.totalWaiting()).isEqualTo(1);
        assertThat(state.enterable()).isFalse();
    }

    /**
     * 기존 entryToken이 만료되면 다음 대기 사용자가 입장 가능 상태로 전환되는지 확인합니다.
     */
    @Test
    void nextWaitingUserBecomesEnterableAfterTokenExpires() throws InterruptedException {
        String token = "short-lived-capacity-token";
        issuedTokens.add(token);
        issue(FIRST_USER_ID, token, 1, 30);

        Thread.sleep(50);

        QueueAdmissionState state = queueRedisRepository.getAdmissionState(
                SCHEDULE_ID,
                SECOND_USER_ID,
                1
        );

        assertThat(state.enterable()).isTrue();
    }

    /**
     * 동시에 입장을 요청해도 설정한 활성 토큰 수를 초과하지 않는지 확인합니다.
     */
    /**
     * entry slot 반환 이벤트가 처리되면 다음 대기 사용자가 즉시 입장 가능해지는지 확인합니다.
     */
    @Test
    void nextWaitingUserBecomesEnterableAfterSlotRelease() {
        String token = "release-capacity-token";
        String entryTokenId = "jti-" + token;
        issuedTokens.add(token);
        issue(FIRST_USER_ID, token, 1, 60_000);

        boolean released = entryTokenRedisRepository.releaseSlot(
                SCHEDULE_ID,
                FIRST_USER_ID,
                entryTokenId
        );
        QueueAdmissionState state = queueRedisRepository.getAdmissionState(
                SCHEDULE_ID,
                SECOND_USER_ID,
                1
        );

        assertThat(released).isTrue();
        assertThat(redisTemplate.hasKey(EntryTokenKey.userToken(SCHEDULE_ID, FIRST_USER_ID)))
                .isFalse();
        assertThat(state.enterable()).isTrue();
    }

    @Test
    void concurrentIssueDoesNotExceedAdmissionCapacity() throws Exception {
        int userCount = 20;
        int capacity = 5;
        ExecutorService executor = Executors.newFixedThreadPool(userCount);
        redisTemplate.delete(QueueKey.waiting(SCHEDULE_ID));

        List<Callable<EntryTokenIssueResult>> tasks = IntStream.rangeClosed(1, userCount)
                .mapToObj(index -> {
                    long userId = 1_000L + index;
                    String token = "concurrent-entry-token-" + index;
                    testUserIds.add(userId);
                    issuedTokens.add(token);
                    redisTemplate.opsForZSet().add(
                            QueueKey.waiting(SCHEDULE_ID),
                            Long.toString(userId),
                            100 + index
                    );
                    return (Callable<EntryTokenIssueResult>)
                            () -> issue(userId, token, capacity, 60_000);
                })
                .toList();

        try {
            List<Future<EntryTokenIssueResult>> futures = executor.invokeAll(tasks);
            long issuedCount = 0;

            for (Future<EntryTokenIssueResult> future : futures) {
                if (future.get().status() == EntryTokenIssueStatus.ISSUED) {
                    issuedCount++;
                }
            }

            assertThat(issuedCount).isEqualTo(capacity);
            assertThat(redisTemplate.opsForZSet().size(QueueKey.activeEntries(SCHEDULE_ID)))
                    .isEqualTo(capacity);
        } finally {
            executor.shutdownNow();
        }
    }

    private EntryTokenIssueResult issue(Long userId, String token, int capacity, long ttlMillis) {
        return entryTokenRedisRepository.issue(
                SCHEDULE_ID,
                userId,
                token,
                "jti-" + token,
                capacity,
                ttlMillis
        );
    }
}
