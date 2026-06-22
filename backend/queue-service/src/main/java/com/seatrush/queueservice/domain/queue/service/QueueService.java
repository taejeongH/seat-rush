package com.seatrush.queueservice.domain.queue.service;

import com.seatrush.queueservice.common.exception.CustomException;
import com.seatrush.queueservice.common.metrics.BusinessMetrics;
import com.seatrush.queueservice.common.response.status.ErrorCode;
import com.seatrush.queueservice.domain.queue.QueueStatus;
import com.seatrush.queueservice.domain.queue.config.QueueAdmissionProperties;
import com.seatrush.queueservice.domain.queue.config.QueuePracticeProperties;
import com.seatrush.queueservice.domain.queue.dto.response.QueueJoinResponseDto;
import com.seatrush.queueservice.domain.queue.dto.response.QueuePositionResponseDto;
import com.seatrush.queueservice.domain.queue.repository.QueueAdmissionState;
import com.seatrush.queueservice.domain.queue.repository.QueueRedisRepository;
import com.seatrush.queueservice.domain.queue.repository.QueueRedisRepository.QueueJoinResult;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 대기열 진입, 순번 조회, heartbeat, 연습 세션 대기열 생성을 담당합니다.
 *
 * 실제 회차와 연습 세션은 같은 Redis 연산을 사용하되 practiceSessionId로 key namespace를 분리합니다.
 */
@Service
public class QueueService {

    private final QueueRedisRepository queueRedisRepository;
    private final QueueAdmissionProperties admissionProperties;
    private final QueuePracticeProperties practiceProperties;
    private final BusinessMetrics businessMetrics;

    public QueueService(
            QueueRedisRepository queueRedisRepository,
            QueueAdmissionProperties admissionProperties,
            QueuePracticeProperties practiceProperties,
            BusinessMetrics businessMetrics
    ) {
        this.queueRedisRepository = queueRedisRepository;
        this.admissionProperties = admissionProperties;
        this.practiceProperties = practiceProperties;
        this.businessMetrics = businessMetrics;
    }

    /**
     * 실제 회차 대기열에 사용자를 진입시킵니다.
     */
    public QueueJoinResponseDto join(Long scheduleId, Long userId) {
        return join(scheduleId, userId, null);
    }

    /**
     * 지정된 회차 또는 연습 세션 대기열에 사용자를 진입시킵니다.
     */
    public QueueJoinResponseDto join(Long scheduleId, Long userId, String practiceSessionId) {
        return businessMetrics.record("queue.join", mode(practiceSessionId), () -> {
            QueueJoinResult joinResult = queueRedisRepository.join(
                    scheduleId,
                    userId,
                    sessionTtlMillis(),
                    practiceSessionId,
                    practiceDataTtlMillis()
            );
            validateScheduleState(joinResult.position());
            long totalWaiting = queueRedisRepository.getWaitingCount(scheduleId, practiceSessionId);

            return new QueueJoinResponseDto(
                    scheduleId,
                    joinResult.position(),
                    totalWaiting,
                    QueueStatus.WAITING,
                    joinResult.alreadyJoined()
            );
        });
    }

    /**
     * 실제 회차 대기열에서 내 순번과 입장 가능 여부를 조회합니다.
     */
    public QueuePositionResponseDto getMyPosition(Long scheduleId, Long userId) {
        return getMyPosition(scheduleId, userId, null);
    }

    /**
     * 지정된 회차 또는 연습 세션 대기열에서 내 순번과 입장 가능 여부를 조회합니다.
     */
    public QueuePositionResponseDto getMyPosition(
            Long scheduleId,
            Long userId,
            String practiceSessionId
    ) {
        return businessMetrics.record("queue.position", mode(practiceSessionId), () -> {
            QueueAdmissionState state = queueRedisRepository.getAdmissionState(
                    scheduleId,
                    userId,
                    admissionProperties.capacity(),
                    practiceSessionId
            );

            if (state.position() == -1) {
                throw new CustomException(ErrorCode.QUEUE_ENTRY_NOT_FOUND);
            }

            return new QueuePositionResponseDto(
                    scheduleId,
                    state.position(),
                    state.totalWaiting(),
                    state.enterable() ? QueueStatus.ENTERABLE : QueueStatus.WAITING
            );
        });
    }

    /**
     * 실제 회차 대기열 세션이 살아 있음을 Redis에 갱신합니다.
     */
    public void heartbeat(Long scheduleId, Long userId) {
        heartbeat(scheduleId, userId, null);
    }

    /**
     * 지정된 회차 또는 연습 세션 대기열 세션이 살아 있음을 Redis에 갱신합니다.
     */
    public void heartbeat(Long scheduleId, Long userId, String practiceSessionId) {
        businessMetrics.record("queue.heartbeat", mode(practiceSessionId), () -> {
            if (!queueRedisRepository.heartbeat(
                    scheduleId,
                    userId,
                    sessionTtlMillis(),
                    practiceSessionId,
                    practiceDataTtlMillis(),
                    practiceTtlRefreshIntervalMillis()
            )) {
                throw new CustomException(ErrorCode.QUEUE_ENTRY_NOT_FOUND);
            }
        });
    }

    /**
     * 연습 세션에 속한 대기열 Redis 데이터를 삭제합니다.
     */
    public void deletePracticeSession(String practiceSessionId) {
        queueRedisRepository.deletePracticeSession(practiceSessionId);
    }

    /**
     * 연습 모드용 가상 회차 상태를 Redis에 생성합니다.
     */
    public void createPracticeSession(
            Long scheduleId,
            String practiceSessionId,
            Instant bookingOpenAt,
            Instant bookingCloseAt
    ) {
        if (!bookingOpenAt.isBefore(bookingCloseAt)) {
            throw new CustomException(ErrorCode.INVALID_PRACTICE_SESSION_TIME);
        }

        queueRedisRepository.createPracticeSession(
                scheduleId,
                practiceSessionId,
                bookingOpenAt,
                bookingCloseAt,
                practiceProperties.dataTtl()
        );
    }

    /**
     * 연습 세션 키가 오래 남지 않도록 TTL을 갱신합니다.
     */
    private long sessionTtlMillis() {
        return admissionProperties.sessionTtl().toMillis();
    }

    private long practiceDataTtlMillis() {
        return practiceProperties.dataTtl().toMillis();
    }

    private long practiceTtlRefreshIntervalMillis() {
        return practiceProperties.ttlRefreshInterval().toMillis();
    }

    private String mode(String practiceSessionId) {
        return practiceSessionId == null || practiceSessionId.isBlank()
                ? "real"
                : "practice";
    }

    private void validateScheduleState(long position) {
        if (position == -1) {
            throw new CustomException(ErrorCode.SCHEDULE_NOT_FOUND);
        }

        if (position == -2) {
            throw new CustomException(ErrorCode.QUEUE_NOT_OPEN);
        }
    }
}
