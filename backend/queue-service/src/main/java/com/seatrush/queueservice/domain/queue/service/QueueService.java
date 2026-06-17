package com.seatrush.queueservice.domain.queue.service;

import com.seatrush.queueservice.common.exception.CustomException;
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
 * 회차별 대기열 진입과 현재 대기 순번 조회를 처리합니다.
 */
@Service
public class QueueService {

    private final QueueRedisRepository queueRedisRepository;
    private final QueueAdmissionProperties admissionProperties;
    private final QueuePracticeProperties practiceProperties;

    public QueueService(
            QueueRedisRepository queueRedisRepository,
            QueueAdmissionProperties admissionProperties,
            QueuePracticeProperties practiceProperties
    ) {
        this.queueRedisRepository = queueRedisRepository;
        this.admissionProperties = admissionProperties;
        this.practiceProperties = practiceProperties;
    }

    /**
     * 사용자를 회차 대기열에 등록하고 현재 순번을 반환합니다.
     * 이미 등록된 사용자의 재요청은 새 순번을 만들지 않고 기존 순번을 반환합니다.
     */
    public QueueJoinResponseDto join(Long scheduleId, Long userId) {
        return join(scheduleId, userId, null);
    }

    public QueueJoinResponseDto join(Long scheduleId, Long userId, String practiceSessionId) {
        QueueJoinResult joinResult = queueRedisRepository.join(
                scheduleId,
                userId,
                sessionTtlMillis(),
                practiceSessionId
        );
        validateScheduleState(joinResult.position());
        expirePracticeSessionKeys(scheduleId, practiceSessionId);
        long totalWaiting = queueRedisRepository.getWaitingCount(scheduleId, practiceSessionId);

        return new QueueJoinResponseDto(
                scheduleId,
                joinResult.position(),
                totalWaiting,
                QueueStatus.WAITING,
                joinResult.alreadyJoined()
        );
    }

    /**
     * 사용자의 현재 대기 순번을 조회합니다.
     */
    public QueuePositionResponseDto getMyPosition(Long scheduleId, Long userId) {
        return getMyPosition(scheduleId, userId, null);
    }

    public QueuePositionResponseDto getMyPosition(
            Long scheduleId,
            Long userId,
            String practiceSessionId
    ) {
        QueueAdmissionState state = queueRedisRepository.getAdmissionState(
                scheduleId,
                userId,
                admissionProperties.capacity(),
                sessionTtlMillis(),
                practiceSessionId
        );

        if (state.position() == -1) {
            throw new CustomException(ErrorCode.QUEUE_ENTRY_NOT_FOUND);
        }

        expirePracticeSessionKeys(scheduleId, practiceSessionId);

        return new QueuePositionResponseDto(
                scheduleId,
                state.position(),
                state.totalWaiting(),
                state.enterable() ? QueueStatus.ENTERABLE : QueueStatus.WAITING
        );
    }

    /**
     * 대기 화면에 머무르는 사용자의 대기열 session TTL을 갱신합니다.
     */
    public void heartbeat(Long scheduleId, Long userId) {
        heartbeat(scheduleId, userId, null);
    }

    public void heartbeat(Long scheduleId, Long userId, String practiceSessionId) {
        if (!queueRedisRepository.heartbeat(
                scheduleId,
                userId,
                sessionTtlMillis(),
                practiceSessionId
        )) {
            throw new CustomException(ErrorCode.QUEUE_ENTRY_NOT_FOUND);
        }
        expirePracticeSessionKeys(scheduleId, practiceSessionId);
    }

    public void deletePracticeSession(String practiceSessionId) {
        queueRedisRepository.deletePracticeSession(practiceSessionId);
    }

    public void createPracticeSession(
            Long scheduleId,
            String practiceSessionId,
            Instant bookingOpenAt,
            Instant bookingCloseAt
    ) {
        if (!bookingOpenAt.isBefore(bookingCloseAt)) {
            throw new IllegalArgumentException("practice session open time must be before close time.");
        }

        queueRedisRepository.createPracticeSession(
                scheduleId,
                practiceSessionId,
                bookingOpenAt,
                bookingCloseAt
        );
        expirePracticeSessionKeys(scheduleId, practiceSessionId);
    }

    public void expirePracticeSessionKeys(Long scheduleId, String practiceSessionId) {
        queueRedisRepository.expirePracticeSessionKeys(
                scheduleId,
                practiceSessionId,
                practiceProperties.dataTtl()
        );
    }

    private long sessionTtlMillis() {
        return admissionProperties.sessionTtl().toMillis();
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
