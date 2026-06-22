package com.seatrush.queueservice.domain.entrytoken.service;

import com.seatrush.queueservice.common.exception.CustomException;
import com.seatrush.queueservice.common.metrics.BusinessMetrics;
import com.seatrush.queueservice.common.response.status.ErrorCode;
import com.seatrush.queueservice.domain.entrytoken.EntryTokenCandidate;
import com.seatrush.queueservice.domain.entrytoken.EntryTokenProvider;
import com.seatrush.queueservice.domain.entrytoken.config.EntryTokenProperties;
import com.seatrush.queueservice.domain.entrytoken.dto.response.EntryTokenIssueResponseDto;
import com.seatrush.queueservice.domain.entrytoken.repository.EntryTokenIssueResult;
import com.seatrush.queueservice.domain.entrytoken.repository.EntryTokenIssueStatus;
import com.seatrush.queueservice.domain.entrytoken.repository.EntryTokenRedisRepository;
import com.seatrush.queueservice.domain.queue.config.QueueAdmissionProperties;
import com.seatrush.queueservice.domain.queue.config.QueuePracticeProperties;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 입장 가능 사용자에게 entryToken을 발급합니다.
 *
 * Redis Lua Script로 대기열 순번 확인, 활성 입장 슬롯 점유, 토큰 저장을 원자적으로 처리합니다.
 */
@Service
public class EntryTokenService {

    private final EntryTokenRedisRepository entryTokenRedisRepository;
    private final EntryTokenProvider entryTokenProvider;
    private final EntryTokenProperties properties;
    private final QueueAdmissionProperties admissionProperties;
    private final QueuePracticeProperties practiceProperties;
    private final BusinessMetrics businessMetrics;

    public EntryTokenService(
            EntryTokenRedisRepository entryTokenRedisRepository,
            EntryTokenProvider entryTokenProvider,
            EntryTokenProperties properties,
            QueueAdmissionProperties admissionProperties,
            QueuePracticeProperties practiceProperties,
            BusinessMetrics businessMetrics
    ) {
        this.entryTokenRedisRepository = entryTokenRedisRepository;
        this.entryTokenProvider = entryTokenProvider;
        this.properties = properties;
        this.admissionProperties = admissionProperties;
        this.practiceProperties = practiceProperties;
        this.businessMetrics = businessMetrics;
    }

    /**
     * 실제 회차용 entryToken을 발급합니다.
     */
    public EntryTokenIssueResponseDto issue(Long scheduleId, Long userId) {
        return issue(scheduleId, userId, null);
    }

    /**
     * 지정된 회차 또는 연습 세션용 entryToken을 발급합니다.
     */
    public EntryTokenIssueResponseDto issue(
            Long scheduleId,
            Long userId,
            String practiceSessionId
    ) {
        return businessMetrics.record("entry_token.issue", mode(practiceSessionId), () -> {
            String mode = mode(practiceSessionId);
            EntryTokenCandidate candidate = businessMetrics.record(
                    "entry_token.issue.sign",
                    mode,
                    () -> entryTokenProvider.create(scheduleId, userId, practiceSessionId)
            );
            EntryTokenIssueResult result = businessMetrics.record(
                    "entry_token.issue.redis",
                    mode,
                    () -> entryTokenRedisRepository.issue(
                            scheduleId,
                            userId,
                            candidate.token(),
                            candidate.jti(),
                            admissionProperties.capacity(),
                            properties.ttl().toMillis(),
                            practiceSessionId,
                            practiceDataTtlMillis(practiceSessionId)
                    )
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
        });
    }

    private String mode(String practiceSessionId) {
        return practiceSessionId == null || practiceSessionId.isBlank()
                ? "real"
                : "practice";
    }

    private long practiceDataTtlMillis(String practiceSessionId) {
        return practiceSessionId == null || practiceSessionId.isBlank()
                ? 0
                : practiceProperties.dataTtl().toMillis();
    }
}
