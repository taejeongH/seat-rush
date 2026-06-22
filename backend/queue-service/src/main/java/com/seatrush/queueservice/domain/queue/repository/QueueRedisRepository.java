package com.seatrush.queueservice.domain.queue.repository;

import com.seatrush.queueservice.domain.queue.QueueKey;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Redis Sorted Set과 Hash를 사용해 회차별 대기열 상태를 저장하고 조회합니다.
 *
 * 대기열 진입, 입장 가능 여부 계산, heartbeat 갱신은 Lua Script로 실행해
 * 여러 Redis 명령이 하나의 원자적 작업처럼 처리되도록 합니다.
 */
@Repository
public class QueueRedisRepository {

    /**
     * 대기 순번, 전체 대기자 수, 입장 가능 여부를 한 번에 계산합니다.
     */
    private static final DefaultRedisScript<List> GET_ADMISSION_STATE_SCRIPT = new DefaultRedisScript<>();

    /**
     * 회차 상태 검증과 중복 진입 방지, 순번 발급을 원자적으로 처리합니다.
     */
    private static final DefaultRedisScript<List> JOIN_QUEUE_SCRIPT = new DefaultRedisScript<>();

    /**
     * 현재 대기열에 남아 있는 사용자의 세션 TTL을 갱신합니다.
     */
    private static final DefaultRedisScript<Long> HEARTBEAT_SCRIPT = new DefaultRedisScript<>();

    static {
        GET_ADMISSION_STATE_SCRIPT.setLocation(new ClassPathResource("scripts/get_admission_state.lua"));
        GET_ADMISSION_STATE_SCRIPT.setResultType(List.class);

        JOIN_QUEUE_SCRIPT.setLocation(new ClassPathResource("scripts/join_queue.lua"));
        JOIN_QUEUE_SCRIPT.setResultType(List.class);

        HEARTBEAT_SCRIPT.setLocation(new ClassPathResource("scripts/heartbeat.lua"));
        HEARTBEAT_SCRIPT.setResultType(Long.class);
    }

    private final RedisTemplate<String, String> redisTemplate;

    public QueueRedisRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 실제 회차 대기열에 사용자를 등록합니다.
     */
    public QueueJoinResult join(Long scheduleId, Long userId, long sessionTtlMillis) {
        return join(scheduleId, userId, sessionTtlMillis, null, 0);
    }

    /**
     * 지정된 회차 또는 연습 세션 대기열에 사용자를 등록합니다.
     *
     * Lua Script는 회차 오픈 시간 검증, 중복 진입 확인, 순번 증가, 세션 만료 정보를 함께 처리합니다.
     */
    public QueueJoinResult join(
            Long scheduleId,
            Long userId,
            long sessionTtlMillis,
            String practiceSessionId,
            long practiceDataTtlMillis
    ) {
        List<Long> result = redisTemplate.execute(
                JOIN_QUEUE_SCRIPT,
                List.of(
                        QueueKey.waiting(scheduleId, practiceSessionId),
                        QueueKey.sequence(scheduleId, practiceSessionId),
                        QueueKey.scheduleState(scheduleId, practiceSessionId),
                        QueueKey.sessionExpirations(scheduleId, practiceSessionId),
                        QueueKey.session(scheduleId, userId, practiceSessionId)
                ),
                userId.toString(),
                Long.toString(sessionTtlMillis),
                Long.toString(practiceDataTtlMillis)
        );

        if (result == null || result.size() != 2) {
            throw new IllegalStateException("대기열 진입 결과를 확인할 수 없습니다.");
        }

        return new QueueJoinResult(
                result.get(0),
                result.get(1) == 1
        );
    }

    /**
     * 실제 회차 대기열에서 사용자의 0-based rank를 조회합니다.
     */
    public Long getRank(Long scheduleId, Long userId) {
        return redisTemplate.opsForZSet()
                .rank(QueueKey.waiting(scheduleId), userId.toString());
    }

    /**
     * 실제 회차 대기열에 남아 있는 사용자 수를 조회합니다.
     */
    public long getWaitingCount(Long scheduleId) {
        Long count = redisTemplate.opsForZSet().size(QueueKey.waiting(scheduleId));
        return count == null ? 0 : count;
    }

    /**
     * 지정된 회차 또는 연습 세션 대기열에 남아 있는 사용자 수를 조회합니다.
     */
    public long getWaitingCount(Long scheduleId, String practiceSessionId) {
        Long count = redisTemplate.opsForZSet().size(QueueKey.waiting(scheduleId, practiceSessionId));
        return count == null ? 0 : count;
    }

    /**
     * 실제 회차에서 사용자의 대기 상태와 입장 가능 여부를 조회합니다.
     */
    /**
     * 지정된 회차 또는 연습 세션에서 사용자의 대기 상태와 입장 가능 여부를 조회합니다.
     *
     * 만료된 세션을 먼저 정리한 뒤 현재 rank와 활성 입장 인원을 기준으로 enter 가능 여부를 계산합니다.
     */
    public QueueAdmissionState getAdmissionState(
            Long scheduleId,
            Long userId,
            int admissionCapacity,
            String practiceSessionId
    ) {
        List<Object> result = redisTemplate.execute(
                GET_ADMISSION_STATE_SCRIPT,
                List.of(
                        QueueKey.waiting(scheduleId, practiceSessionId),
                        QueueKey.activeEntries(scheduleId, practiceSessionId),
                        QueueKey.sessionExpirations(scheduleId, practiceSessionId)
                ),
                userId.toString(),
                Integer.toString(admissionCapacity)
        );

        if (result == null || result.size() != 3) {
            throw new IllegalStateException("대기열 상태 조회 결과를 확인할 수 없습니다.");
        }

        return new QueueAdmissionState(
                toLong(result.get(0)),
                toLong(result.get(1)),
                toLong(result.get(2)) == 1
        );
    }

    /**
     * 테스트와 기존 호출부를 위한 기본 세션 TTL 기반 대기 상태 조회입니다.
     */
    public QueueAdmissionState getAdmissionState(
            Long scheduleId,
            Long userId,
            int admissionCapacity
    ) {
        return getAdmissionState(scheduleId, userId, admissionCapacity, null);
    }

    /**
     * Redis Script 결과 타입을 long으로 변환합니다.
     */
    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    /**
     * 실제 회차 대기열에서 사용자 세션의 만료 시간을 연장합니다.
     */
    public boolean heartbeat(Long scheduleId, Long userId, long sessionTtlMillis) {
        return heartbeat(scheduleId, userId, sessionTtlMillis, null, 0, 0);
    }

    /**
     * 지정된 회차 또는 연습 세션 대기열에서 사용자 세션의 만료 시간을 연장합니다.
     */
    public boolean heartbeat(
            Long scheduleId,
            Long userId,
            long sessionTtlMillis,
            String practiceSessionId,
            long practiceDataTtlMillis,
            long practiceTtlRefreshIntervalMillis
    ) {
        Long result = redisTemplate.execute(
                HEARTBEAT_SCRIPT,
                List.of(
                        QueueKey.waiting(scheduleId, practiceSessionId),
                        QueueKey.sessionExpirations(scheduleId, practiceSessionId),
                        QueueKey.session(scheduleId, userId, practiceSessionId),
                        QueueKey.scheduleState(scheduleId, practiceSessionId),
                        QueueKey.sequence(scheduleId, practiceSessionId),
                        QueueKey.activeEntries(scheduleId, practiceSessionId)
                ),
                userId.toString(),
                Long.toString(sessionTtlMillis),
                Long.toString(practiceDataTtlMillis),
                Long.toString(practiceTtlRefreshIntervalMillis)
        );
        return result != null && result == 1L;
    }

    /**
     * 연습 세션에 속한 Redis 키를 즉시 삭제합니다.
     */
    public void deletePracticeSession(String practiceSessionId) {
        Set<String> keys = redisTemplate.keys(QueueKey.practiceKeys(practiceSessionId));
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    /**
     * 연습 세션 대기열 관련 키에 TTL을 적용합니다.
     */
    /**
     * 연습 모드에서 사용할 가상 회차 상태를 Redis에 생성합니다.
     */
    public void createPracticeSession(
            Long scheduleId,
            String practiceSessionId,
            Instant bookingOpenAt,
            Instant bookingCloseAt,
            Duration dataTtl
    ) {
        String scheduleStateKey = QueueKey.scheduleState(scheduleId, practiceSessionId);
        redisTemplate.opsForHash().putAll(
                scheduleStateKey,
                Map.of(
                        "status", "BOOKING_OPEN",
                        "bookingOpenAt", Long.toString(bookingOpenAt.toEpochMilli()),
                        "bookingCloseAt", Long.toString(bookingCloseAt.toEpochMilli()),
                        "version", "0"
                )
        );
        redisTemplate.expire(scheduleStateKey, dataTtl);
    }

    /**
     * 대기열 진입 결과입니다.
     */
    public record QueueJoinResult(
            long position,
            boolean alreadyJoined
    ) {
    }
}
