package com.seatrush.ticketservice.domain.concert.controller;

import com.seatrush.ticketservice.common.response.ApiResponse;
import com.seatrush.ticketservice.common.response.status.SuccessCode;
import com.seatrush.ticketservice.domain.concert.dto.request.ScheduleCreateRequestDto;
import com.seatrush.ticketservice.domain.concert.dto.request.ScheduleUpdateRequestDto;
import com.seatrush.ticketservice.domain.concert.dto.response.ConcertScheduleResponseDto;
import com.seatrush.ticketservice.domain.concert.dto.response.ScheduleSyncResponseDto;
import com.seatrush.ticketservice.domain.concert.service.ScheduleCommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Schedule Admin", description = "회차 관리 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/admin")
public class ScheduleController {

    private final ScheduleCommandService scheduleCommandService;

    public ScheduleController(ScheduleCommandService scheduleCommandService) {
        this.scheduleCommandService = scheduleCommandService;
    }

    @Operation(summary = "회차 생성", description = "관리자가 공연에 새로운 회차를 추가합니다.")
    @PostMapping("/concerts/{concertId}/schedules")
    public ResponseEntity<ApiResponse<ConcertScheduleResponseDto>> createSchedule(
            @Parameter(description = "공연 ID", example = "1")
            @PathVariable Long concertId,
            @Valid @RequestBody ScheduleCreateRequestDto request
    ) {
        return ApiResponse.onSuccess(
                SuccessCode.CREATED,
                scheduleCommandService.create(concertId, request)
        );
    }

    @Operation(summary = "회차 수정", description = "관리자가 회차 시간과 상태를 수정합니다.")
    @PatchMapping("/schedules/{scheduleId}")
    public ResponseEntity<ApiResponse<ConcertScheduleResponseDto>> updateSchedule(
            @Parameter(description = "회차 ID", example = "1")
            @PathVariable Long scheduleId,
            @Valid @RequestBody ScheduleUpdateRequestDto request
    ) {
        return ApiResponse.onSuccess(
                SuccessCode.OK,
                scheduleCommandService.update(scheduleId, request)
        );
    }

    @Operation(summary = "회차 취소", description = "관리자가 회차를 삭제하지 않고 취소 상태로 변경합니다.")
    @DeleteMapping("/schedules/{scheduleId}")
    public ResponseEntity<ApiResponse<ConcertScheduleResponseDto>> cancelSchedule(
            @Parameter(description = "회차 ID", example = "1")
            @PathVariable Long scheduleId
    ) {
        return ApiResponse.onSuccess(
                SuccessCode.OK,
                scheduleCommandService.cancel(scheduleId)
        );
    }

    @Operation(summary = "전체 회차 동기화", description = "현재 모든 회차 상태를 Outbox 이벤트로 다시 기록합니다.")
    @PostMapping("/schedules/sync")
    public ResponseEntity<ApiResponse<ScheduleSyncResponseDto>> synchronizeSchedules() {
        return ApiResponse.onSuccess(
                SuccessCode.ACCEPTED,
                scheduleCommandService.synchronizeAll()
        );
    }
}
