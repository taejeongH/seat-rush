package com.seatrush.queueservice.domain.entrytoken.service;

import com.seatrush.queueservice.common.exception.CustomException;
import com.seatrush.queueservice.common.response.status.ErrorCode;
import com.seatrush.queueservice.domain.entrytoken.EntryTokenCandidate;
import com.seatrush.queueservice.domain.entrytoken.EntryTokenProvider;
import com.seatrush.queueservice.domain.entrytoken.config.EntryTokenProperties;
import com.seatrush.queueservice.domain.entrytoken.dto.response.EntryTokenIssueResponseDto;
import com.seatrush.queueservice.domain.entrytoken.repository.EntryTokenIssueResult;
import com.seatrush.queueservice.domain.entrytoken.repository.EntryTokenIssueStatus;
import com.seatrush.queueservice.domain.entrytoken.repository.EntryTokenRedisRepository;
import com.seatrush.queueservice.domain.queue.config.QueueAdmissionProperties;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 대기 순번에 따른 입장 허용과 entryToken 발급 및 검증을 처리합니다.
 */
@Service
public class EntryTokenService {

    private final EntryTokenRedisRepository entryTokenRedisRepository;
    private final EntryTokenProvider entryTokenProvider;
    private final EntryTokenProperties properties;
    private final QueueAdmissionProperties admissionProperties;

    public EntryTokenService(
            EntryTokenRedisRepository entryTokenRedisRepository,
            EntryTokenProvider entryTokenProvider,
            EntryTokenProperties properties,
            QueueAdmissionProperties admissionProperties
    ) {
        this.entryTokenRedisRepository = entryTokenRedisRepository;
        this.entryTokenProvider = entryTokenProvider;
        this.properties = properties;
        this.admissionProperties = admissionProperties;
    }

    /**
     * 입장 가능한 사용자에게 제한 시간 동안 사용할 entryToken을 발급합니다.
     */
    public EntryTokenIssueResponseDto issue(Long scheduleId, Long userId) {
        EntryTokenCandidate candidate = entryTokenProvider.create(scheduleId, userId);
        EntryTokenIssueResult result = entryTokenRedisRepository.issue(
                scheduleId,
                userId,
                candidate.token(),
                candidate.jti(),
                admissionProperties.capacity(),
                properties.ttl().toMillis()
        );

        if (result.status() == EntryTokenIssueStatus.QUEUE_ENTRY_NOT_FOUND) {
            throw new CustomException(ErrorCode.QUEUE_ENTRY_NOT_FOUND);
        }

        if (result.status() == EntryTokenIssueStatus.ENTRY_NOT_ALLOWED) {
            throw new CustomException(ErrorCode.ENTRY_NOT_ALLOWED);
        }

        if (result.status() == EntryTokenIssueStatus.SCHEDULE_NOT_FOUND) {
            throw new CustomException(ErrorCode.SCHEDULE_NOT_FOUND);
        }

        if (result.status() == EntryTokenIssueStatus.QUEUE_NOT_OPEN) {
            throw new CustomException(ErrorCode.QUEUE_NOT_OPEN);
        }

        return new EntryTokenIssueResponseDto(
                scheduleId,
                result.entryToken(),
                Instant.ofEpochMilli(result.expiresAt()),
                result.status() == EntryTokenIssueStatus.ALREADY_ISSUED
        );
    }
}
