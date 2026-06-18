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

/**
 * 실제 회차 대기열 진입, 순번 조회, heartbeat, 입장 토큰 발급 API를 제공합니다.
 *
 * 사용자 식별은 API Gateway가 검증한 뒤 전달하는 X-User-Id 헤더를 기준으로 합니다.
 */
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

    /**
     * 티켓 오픈 시 회차 대기열에 진입합니다.
     */
    @Operation(
            summary = "대기열 진입",
            description = "회차 오픈 시간과 상태를 확인한 뒤 사용자를 Redis Sorted Set 대기열에 등록합니다."
    )
    @PostMapping("/join")
    public ResponseEntity<ApiResponse<QueueJoinResponseDto>> join(
            @Parameter(description = "회차 ID")
            @Positive @PathVariable Long scheduleId,
            @Parameter(hidden = true)
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ApiResponse.onSuccess(SuccessCode.OK, queueService.join(scheduleId, userId));
    }

    /**
     * 현재 사용자의 대기 순번과 입장 가능 상태를 조회합니다.
     */
    @Operation(
            summary = "내 대기 순번 조회",
            description = "만료된 대기 세션을 정리한 뒤 현재 사용자의 순번과 입장 가능 여부를 반환합니다."
    )
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<QueuePositionResponseDto>> getMyPosition(
            @Parameter(description = "회차 ID")
            @Positive @PathVariable Long scheduleId,
            @Parameter(hidden = true)
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ApiResponse.onSuccess(SuccessCode.OK, queueService.getMyPosition(scheduleId, userId));
    }

    /**
     * 대기 화면을 유지 중인 사용자의 세션 TTL을 갱신합니다.
     */
    @Operation(
            summary = "대기열 heartbeat",
            description = "대기 중인 사용자의 세션 만료 시간을 갱신해 브라우저 이탈 사용자를 정리할 수 있게 합니다."
    )
    @PostMapping("/heartbeat")
    public ResponseEntity<ApiResponse<Void>> heartbeat(
            @Parameter(description = "회차 ID")
            @Positive @PathVariable Long scheduleId,
            @Parameter(hidden = true)
            @RequestHeader("X-User-Id") Long userId
    ) {
        queueService.heartbeat(scheduleId, userId);
        return ApiResponse.onSuccess(SuccessCode.OK, null);
    }

    /**
     * 입장 가능 상태인 사용자에게 좌석 선택 단계에서 사용할 entryToken을 발급합니다.
     */
    @Operation(
            summary = "entryToken 발급",
            description = "대기 순번과 활성 입장 슬롯을 확인한 뒤 좌석 선택 권한을 나타내는 entryToken을 발급합니다."
    )
    @PostMapping("/enter")
    public ResponseEntity<ApiResponse<EntryTokenIssueResponseDto>> enter(
            @Parameter(description = "회차 ID")
            @Positive @PathVariable Long scheduleId,
            @Parameter(hidden = true)
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ApiResponse.onSuccess(SuccessCode.OK, entryTokenService.issue(scheduleId, userId));
    }
}
