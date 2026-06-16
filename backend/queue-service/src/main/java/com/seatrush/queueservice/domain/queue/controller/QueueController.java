package com.seatrush.queueservice.domain.queue.controller;

import com.seatrush.queueservice.common.response.ApiResponse;
import com.seatrush.queueservice.common.response.status.SuccessCode;
import com.seatrush.queueservice.domain.entrytoken.dto.response.EntryTokenIssueResponseDto;
import com.seatrush.queueservice.domain.entrytoken.service.EntryTokenService;
import com.seatrush.queueservice.domain.queue.dto.response.QueueJoinResponseDto;
import com.seatrush.queueservice.domain.queue.dto.response.QueuePositionResponseDto;
import com.seatrush.queueservice.domain.queue.service.QueueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Queue", description = "Schedule queue API")
@SecurityRequirement(name = "bearerAuth")
@Validated
@RestController
@RequestMapping("/api/schedules/{scheduleId}/queues")
public class QueueController {

    private final QueueService queueService;
    private final EntryTokenService entryTokenService;

    public QueueController(QueueService queueService, EntryTokenService entryTokenService) {
        this.queueService = queueService;
        this.entryTokenService = entryTokenService;
    }

    @Operation(
            summary = "대기열 진입",
            description = "사용자를 회차별 대기열에 등록하고 현재 대기 순번을 반환합니다."
    )
    @PostMapping("/join")
    public ResponseEntity<ApiResponse<QueueJoinResponseDto>> join(
            @Parameter(description = "회차 ID", example = "1")
            @Positive @PathVariable Long scheduleId,
            @Parameter(hidden = true)
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ApiResponse.onSuccess(SuccessCode.OK, queueService.join(scheduleId, userId));
    }

    @Operation(
            summary = "내 대기 순번 조회",
            description = "회차 대기열에 진입한 사용자의 현재 순번을 조회하고 session TTL을 갱신합니다."
    )
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<QueuePositionResponseDto>> getMyPosition(
            @Parameter(description = "회차 ID", example = "1")
            @Positive @PathVariable Long scheduleId,
            @Parameter(hidden = true)
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ApiResponse.onSuccess(SuccessCode.OK, queueService.getMyPosition(scheduleId, userId));
    }

    @Operation(
            summary = "대기열 heartbeat",
            description = "대기 화면에 머무르는 사용자의 대기열 session TTL을 갱신합니다."
    )
    @PostMapping("/heartbeat")
    public ResponseEntity<ApiResponse<Void>> heartbeat(
            @Parameter(description = "회차 ID", example = "1")
            @Positive @PathVariable Long scheduleId,
            @Parameter(hidden = true)
            @RequestHeader("X-User-Id") Long userId
    ) {
        queueService.heartbeat(scheduleId, userId);
        return ApiResponse.onSuccess(SuccessCode.OK, null);
    }

    @Operation(
            summary = "좌석 선택 단계 입장",
            description = "입장 가능한 순번의 사용자에게 제한 시간 동안 사용할 entryToken을 발급합니다."
    )
    @PostMapping("/enter")
    public ResponseEntity<ApiResponse<EntryTokenIssueResponseDto>> enter(
            @Parameter(description = "회차 ID", example = "1")
            @Positive @PathVariable Long scheduleId,
            @Parameter(hidden = true)
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ApiResponse.onSuccess(SuccessCode.OK, entryTokenService.issue(scheduleId, userId));
    }
}
