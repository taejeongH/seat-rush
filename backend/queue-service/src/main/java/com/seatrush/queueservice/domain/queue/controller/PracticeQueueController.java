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

    @Operation(
            summary = "연습 모드 대기열 세션 생성",
            description = "practiceSessionId 기준으로 분리된 연습용 대기열 상태를 생성합니다."
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

    @Operation(
            summary = "연습 모드 대기열 데이터 정리",
            description = "practiceSessionId 기준으로 분리된 연습용 대기열 데이터를 삭제합니다."
    )
    @DeleteMapping("/queues/sessions/{practiceSessionId}")
    public ResponseEntity<ApiResponse<Void>> deleteSession(
            @PathVariable String practiceSessionId
    ) {
        queueService.deletePracticeSession(practiceSessionId);
        return ApiResponse.onSuccess(SuccessCode.OK, null);
    }

    @Operation(
            summary = "연습 모드 대기열 진입",
            description = "연습 세션과 좌석 배치도 기준으로 분리된 대기열에 사용자를 등록합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/sessions/{practiceSessionId}/seat-layouts/{seatLayoutId}/queues/join")
    public ResponseEntity<ApiResponse<QueueJoinResponseDto>> join(
            @PathVariable String practiceSessionId,
            @Parameter(description = "좌석 배치도 ID", example = "1")
            @Positive @PathVariable Long seatLayoutId,
            @Parameter(hidden = true)
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ApiResponse.onSuccess(
                SuccessCode.OK,
                queueService.join(seatLayoutId, userId, practiceSessionId)
        );
    }

    @Operation(
            summary = "연습 모드 내 대기 순번 조회",
            description = "연습 세션과 좌석 배치도 기준의 현재 대기 순번을 조회합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/sessions/{practiceSessionId}/seat-layouts/{seatLayoutId}/queues/me")
    public ResponseEntity<ApiResponse<QueuePositionResponseDto>> getMyPosition(
            @PathVariable String practiceSessionId,
            @Parameter(description = "좌석 배치도 ID", example = "1")
            @Positive @PathVariable Long seatLayoutId,
            @Parameter(hidden = true)
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ApiResponse.onSuccess(
                SuccessCode.OK,
                queueService.getMyPosition(seatLayoutId, userId, practiceSessionId)
        );
    }

    @Operation(
            summary = "연습 모드 대기열 heartbeat",
            description = "연습 대기열에 머무르는 사용자의 session TTL을 갱신합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/sessions/{practiceSessionId}/seat-layouts/{seatLayoutId}/queues/heartbeat")
    public ResponseEntity<ApiResponse<Void>> heartbeat(
            @PathVariable String practiceSessionId,
            @Parameter(description = "좌석 배치도 ID", example = "1")
            @Positive @PathVariable Long seatLayoutId,
            @Parameter(hidden = true)
            @RequestHeader("X-User-Id") Long userId
    ) {
        queueService.heartbeat(seatLayoutId, userId, practiceSessionId);
        return ApiResponse.onSuccess(SuccessCode.OK, null);
    }

    @Operation(
            summary = "연습 모드 좌석 선택 단계 입장",
            description = "연습 대기열에서 입장 가능한 사용자에게 entryToken을 발급합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/sessions/{practiceSessionId}/seat-layouts/{seatLayoutId}/queues/enter")
    public ResponseEntity<ApiResponse<EntryTokenIssueResponseDto>> enter(
            @PathVariable String practiceSessionId,
            @Parameter(description = "좌석 배치도 ID", example = "1")
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
