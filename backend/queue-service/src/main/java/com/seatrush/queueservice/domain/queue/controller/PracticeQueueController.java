package com.seatrush.queueservice.domain.queue.controller;

import com.seatrush.queueservice.common.response.ApiResponse;
import com.seatrush.queueservice.common.response.status.SuccessCode;
import com.seatrush.queueservice.domain.entrytoken.dto.response.EntryTokenIssueResponseDto;
import com.seatrush.queueservice.domain.entrytoken.service.EntryTokenService;
import com.seatrush.queueservice.domain.queue.dto.request.PracticeQueueSessionCreateRequestDto;
import com.seatrush.queueservice.domain.queue.dto.response.QueueJoinResponseDto;
import com.seatrush.queueservice.domain.queue.dto.response.QueuePositionResponseDto;
import com.seatrush.queueservice.domain.queue.service.QueueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 연습 모드 대기열 API를 제공합니다.
 *
 * 실제 회차 ID 대신 seatLayoutId와 practiceSessionId를 조합해 Redis key를 분리합니다.
 */
@Tag(name = "Practice Queue", description = "Practice mode queue API")
@Validated
@RestController
@RequestMapping("/api/practice")
public class PracticeQueueController {

    private final QueueService queueService;
    private final EntryTokenService entryTokenService;

    public PracticeQueueController(
            QueueService queueService,
            EntryTokenService entryTokenService
    ) {
        this.queueService = queueService;
        this.entryTokenService = entryTokenService;
    }

    /**
     * 연습 모드에서 사용할 가상 대기열 세션을 생성합니다.
     */
    @Operation(
            summary = "연습 대기열 세션 생성",
            description = "좌석 배치도 ID를 기준으로 연습용 오픈 시각과 마감 시각을 Redis에 저장합니다."
    )
    @PostMapping("/queues/sessions")
    public ResponseEntity<ApiResponse<Void>> createSession(
            @Valid @RequestBody PracticeQueueSessionCreateRequestDto request
    ) {
        queueService.createPracticeSession(
                request.seatLayoutId(),
                request.practiceSessionId(),
                request.bookingOpenAt(),
                request.bookingCloseAt()
        );
        return ApiResponse.onSuccess(SuccessCode.OK, null);
    }

    /**
     * 연습 세션 종료 후 Redis에 남은 대기열 데이터를 정리합니다.
     */
    @Operation(
            summary = "연습 대기열 세션 삭제",
            description = "practiceSessionId에 속한 연습용 Redis 키를 삭제합니다."
    )
    @DeleteMapping("/queues/sessions/{practiceSessionId}")
    public ResponseEntity<ApiResponse<Void>> deleteSession(
            @PathVariable String practiceSessionId
    ) {
        queueService.deletePracticeSession(practiceSessionId);
        return ApiResponse.onSuccess(SuccessCode.OK, null);
    }

    /**
     * 연습 세션 대기열에 사용자를 진입시킵니다.
     */
    @Operation(
            summary = "연습 대기열 진입",
            description = "practiceSessionId로 분리된 연습 대기열에 사용자를 등록합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/sessions/{practiceSessionId}/seat-layouts/{seatLayoutId}/queues/join")
    public ResponseEntity<ApiResponse<QueueJoinResponseDto>> join(
            @PathVariable String practiceSessionId,
            @Parameter(description = "좌석 배치도 ID")
            @Positive @PathVariable Long seatLayoutId,
            @Parameter(hidden = true)
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ApiResponse.onSuccess(
                SuccessCode.OK,
                queueService.join(seatLayoutId, userId, practiceSessionId)
        );
    }

    /**
     * 연습 세션에서 현재 사용자의 대기 순번과 입장 가능 여부를 조회합니다.
     */
    @Operation(
            summary = "연습 대기 순번 조회",
            description = "practiceSessionId로 분리된 대기열에서 현재 사용자의 순번과 입장 가능 여부를 반환합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/sessions/{practiceSessionId}/seat-layouts/{seatLayoutId}/queues/me")
    public ResponseEntity<ApiResponse<QueuePositionResponseDto>> getMyPosition(
            @PathVariable String practiceSessionId,
            @Parameter(description = "좌석 배치도 ID")
            @Positive @PathVariable Long seatLayoutId,
            @Parameter(hidden = true)
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ApiResponse.onSuccess(
                SuccessCode.OK,
                queueService.getMyPosition(seatLayoutId, userId, practiceSessionId)
        );
    }

    /**
     * 연습 세션 대기열에서 사용자 세션 TTL을 갱신합니다.
     */
    @Operation(
            summary = "연습 대기열 heartbeat",
            description = "연습 대기열에 남아 있는 사용자 세션 만료 시간을 갱신합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/sessions/{practiceSessionId}/seat-layouts/{seatLayoutId}/queues/heartbeat")
    public ResponseEntity<ApiResponse<Void>> heartbeat(
            @PathVariable String practiceSessionId,
            @Parameter(description = "좌석 배치도 ID")
            @Positive @PathVariable Long seatLayoutId,
            @Parameter(hidden = true)
            @RequestHeader("X-User-Id") Long userId
    ) {
        queueService.heartbeat(seatLayoutId, userId, practiceSessionId);
        return ApiResponse.onSuccess(SuccessCode.OK, null);
    }

    /**
     * 연습 세션에서 좌석 선택에 사용할 entryToken을 발급합니다.
     */
    @Operation(
            summary = "연습 entryToken 발급",
            description = "입장 가능한 연습 사용자에게 practiceSessionId가 포함된 entryToken을 발급합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/sessions/{practiceSessionId}/seat-layouts/{seatLayoutId}/queues/enter")
    public ResponseEntity<ApiResponse<EntryTokenIssueResponseDto>> enter(
            @PathVariable String practiceSessionId,
            @Parameter(description = "좌석 배치도 ID")
            @Positive @PathVariable Long seatLayoutId,
            @Parameter(hidden = true)
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ApiResponse.onSuccess(
                SuccessCode.OK,
                entryTokenService.issue(seatLayoutId, userId, practiceSessionId)
        );
    }
}
