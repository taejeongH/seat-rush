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

/**
 * 관리자(Admin) 권한을 지닌 사용자용 공연 회차(Schedule) 제어/관리 컨트롤러입니다.
 * 
 * 신규 회차 등록, 일정 변경, 취소 처리 및 대기열 연동을 위한 카프카 토픽 상태 동기화를 담당합니다.
 */
@Tag(name = "Schedule Admin", description = "회차 관리 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/admin")
public class ScheduleController {

    private final ScheduleCommandService scheduleCommandService;

    public ScheduleController(ScheduleCommandService scheduleCommandService) {
        this.scheduleCommandService = scheduleCommandService;
    }

    /**
     * 특정 공연에 종속된 신규 회차 일정을 등록합니다.
     *
     * @param concertId 공연 고유 식별 ID
     * @param request 신규 등록할 회차 일시 및 좌석 구역 정보 목록 Dto
     * @return 등록 완료된 공연 회차 정보 Dto
     */
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

    /**
     * 기존에 등록된 특정 공연 회차의 개시 일시 및 상태(예매대기, 예매중 등)를 수정합니다.
     *
     * @param scheduleId 공연 회차 ID
     * @param request 변경할 일시 및 상태 정보 Dto
     * @return 수정 완료된 공연 회차 정보 Dto
     */
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

    /**
     * 공연 회차를 소프트 삭제(Soft Delete)하고 상태를 CANCELED(취소됨)로 설정합니다.
     *
     * @param scheduleId 공연 회차 ID
     * @return 취소 완료된 공연 회차 정보 Dto
     */
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

    /**
     * 카프카 메시지 유실 등으로 인해 대기열(Queue Service)에 저장된 회차 설정 상태가 어긋났을 때,
     * RDB 상의 모든 회차 상태를 다시 확인하고 Outbox 이벤트를 기입하여 강제로 동기화하는 보정 API입니다.
     *
     * @return 동기화 요청 결과 통계 Dto
     */
    @Operation(summary = "전체 회차 동기화", description = "현재 모든 회차 상태를 Outbox 이벤트로 다시 기록합니다.")
    @PostMapping("/schedules/sync")
    public ResponseEntity<ApiResponse<ScheduleSyncResponseDto>> synchronizeSchedules() {
        return ApiResponse.onSuccess(
                SuccessCode.ACCEPTED,
                scheduleCommandService.synchronizeAll()
        );
    }
}

