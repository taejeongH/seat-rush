package com.seatrush.ticketservice.domain.seat.controller;

import com.seatrush.ticketservice.common.entrytoken.EntryTokenClaims;
import com.seatrush.ticketservice.common.entrytoken.EntryTokenRequestAttribute;
import com.seatrush.ticketservice.common.entrytoken.EntryTokenRequired;
import com.seatrush.ticketservice.common.response.ApiResponse;
import com.seatrush.ticketservice.common.response.status.SuccessCode;
import com.seatrush.ticketservice.domain.seat.dto.response.SeatResponseDto;
import com.seatrush.ticketservice.domain.seat.dto.response.SeatSectionResponseDto;
import com.seatrush.ticketservice.domain.seat.service.SeatQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 실시간 공연 및 연습 세션의 좌석 배치도 및 구역 상태 조회를 처리하는 컨트롤러입니다.
 * 
 * 대기열 진입 토큰(Entry Token)의 유효성 검증을 필수로 수행합니다.
 */
@Tag(name = "Seat", description = "좌석 조회 API")
@SecurityRequirement(name = "bearerAuth")
@SecurityRequirement(name = "entryToken")
@EntryTokenRequired
@Validated
@RestController
@RequestMapping("/api")
public class SeatController {

    private final SeatQueryService seatQueryService;

    public SeatController(SeatQueryService seatQueryService) {
        this.seatQueryService = seatQueryService;
    }

    /**
     * 특정 공연 회차(Schedule)에 설정된 좌석 구역(예: VIP석, R석 등) 및 가격 정보를 조회합니다.
     *
     * @param scheduleId 공연 회차 ID
     * @param claims 대기열 진입 토큰 정보
     * @return 좌석 구역 목록 Dto
     */
    @Operation(summary = "좌석 구역 조회", description = "회차에 구성된 좌석 구역과 등급, 가격을 조회합니다.")
    @GetMapping("/schedules/{scheduleId}/sections")
    public ResponseEntity<ApiResponse<List<SeatSectionResponseDto>>> getSections(
            @Positive @PathVariable Long scheduleId,
            @Parameter(hidden = true)
            @RequestAttribute(EntryTokenRequestAttribute.CLAIMS) EntryTokenClaims claims
    ) {
        return ApiResponse.onSuccess(
                SuccessCode.OK,
                seatQueryService.getSections(scheduleId, claims)
        );
    }

    /**
     * 특정 공연 회차 및 구역의 개별 좌석들의 DB 상태와 Redis 선점(Hold) 상태를 머지하여 실시간 좌석 목록을 조회합니다.
     *
     * @param scheduleId 공연 회차 ID
     * @param sectionId 좌석 구역 ID
     * @param claims 대기열 진입 토큰 정보
     * @return 개별 좌석 상태 목록 Dto
     */
    @Operation(summary = "좌석 목록 조회", description = "구역별 좌석과 실시간 예매·선점 상태를 조회합니다.")
    @GetMapping("/schedules/{scheduleId}/seats")
    public ResponseEntity<ApiResponse<List<SeatResponseDto>>> getSeats(
            @Positive @PathVariable Long scheduleId,
            @Positive @RequestParam Long sectionId,
            @Parameter(hidden = true)
            @RequestAttribute(EntryTokenRequestAttribute.CLAIMS) EntryTokenClaims claims
    ) {
        return ApiResponse.onSuccess(
                SuccessCode.OK,
                seatQueryService.getSeats(scheduleId, sectionId, claims)
        );
    }
}
