package com.seatrush.queueservice.domain.queue.service;

import com.seatrush.queueservice.common.exception.CustomException;
import com.seatrush.queueservice.common.response.status.ErrorCode;
import com.seatrush.queueservice.domain.queue.QueueStatus;
import com.seatrush.queueservice.domain.queue.config.QueueAdmissionProperties;
import com.seatrush.queueservice.domain.queue.dto.response.QueueJoinResponseDto;
import com.seatrush.queueservice.domain.queue.dto.response.QueuePositionResponseDto;
import com.seatrush.queueservice.domain.queue.repository.QueueAdmissionState;
import com.seatrush.queueservice.domain.queue.repository.QueueRedisRepository;
import com.seatrush.queueservice.domain.queue.repository.QueueRedisRepository.QueueJoinResult;
import org.springframework.stereotype.Service;

/**
 * 회차별 대기열 진입과 현재 대기 순번 조회를 처리합니다.
 */
@Service
public class QueueService {

    private final QueueRedisRepository queueRedisRepository;
    private final QueueAdmissionProperties admissionProperties;

    public QueueService(
            QueueRedisRepository queueRedisRepository,
            QueueAdmissionProperties admissionProperties
    ) {
        this.queueRedisRepository = queueRedisRepository;
        this.admissionProperties = admissionProperties;
    }

    /**
     * 사용자를 회차 대기열에 등록하고 현재 순번을 반환합니다.
     * 이미 등록된 사용자의 재요청은 새 순번을 만들지 않고 기존 순번을 반환합니다.
     */
    public QueueJoinResponseDto join(Long scheduleId, Long userId) {
        QueueJoinResult joinResult = queueRedisRepository.join(scheduleId, userId);
        validateScheduleState(joinResult.position());
        long totalWaiting = queueRedisRepository.getWaitingCount(scheduleId);

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
        QueueAdmissionState state = queueRedisRepository.getAdmissionState(
                scheduleId,
                userId,
                admissionProperties.capacity()
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
