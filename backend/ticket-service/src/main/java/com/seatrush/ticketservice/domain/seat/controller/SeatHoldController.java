package com.seatrush.ticketservice.domain.seat.controller;

import com.seatrush.ticketservice.common.entrytoken.EntryTokenClaims;
import com.seatrush.ticketservice.common.entrytoken.EntryTokenRequestAttribute;
import com.seatrush.ticketservice.common.entrytoken.EntryTokenRequired;
import com.seatrush.ticketservice.common.response.ApiResponse;
import com.seatrush.ticketservice.common.response.status.SuccessCode;
import com.seatrush.ticketservice.domain.seat.dto.request.SeatHoldRequestDto;
import com.seatrush.ticketservice.domain.seat.dto.response.SeatHoldResponseDto;
import com.seatrush.ticketservice.domain.seat.service.SeatHoldService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

/**
 * 실시간 공연 및 연습 세션의 좌석 선점(Hold) 라이프사이클(선점 생성, 선점 조회, 선점 해제)을 처리하는 컨트롤러입니다.
 * 
 * 대기열 진입 토큰(Entry Token)의 유효성 검증을 필수로 수행합니다.
 */
@Tag(name = "Seat Hold", description = "좌석 선점 API")
@SecurityRequirement(name = "bearerAuth")
@SecurityRequirement(name = "entryToken")
@EntryTokenRequired
@Validated
@RestController
@RequestMapping("/api")
public class SeatHoldController {

    private final SeatHoldService seatHoldService;

    public SeatHoldController(SeatHoldService seatHoldService) {
        this.seatHoldService = seatHoldService;
    }

    /**
     * 사용자가 선택한 좌석(들)에 대해 Redis 선점을 시도합니다.
     * 요청한 모든 좌석이 사용 가능할 때만 원자적(Atomic)으로 선점이 성립됩니다.
     *
     * @param scheduleId 공연 회차 ID
     * @param claims 대기열 진입 토큰 정보
     * @param request 선점할 좌석 ID 목록 Dto
     * @return 생성된 좌석 선점 결과 정보 (holdId, 만료시각 등)
     */
    @Operation(
            summary = "좌석 선점",
            description = "선택한 좌석 전체가 사용 가능할 때만 하나의 hold로 선점합니다."
    )
    @PostMapping("/schedules/{scheduleId}/seats/hold")
    public ResponseEntity<ApiResponse<SeatHoldResponseDto>> holdSeats(
            @Positive @PathVariable Long scheduleId,
            @Parameter(hidden = true)
            @RequestAttribute(EntryTokenRequestAttribute.CLAIMS) EntryTokenClaims claims,
            @Valid @RequestBody SeatHoldRequestDto request
    ) {
        return ApiResponse.onSuccess(
                SuccessCode.CREATED,
                seatHoldService.hold(scheduleId, claims, request.seatIds())
        );
    }

    /**
     * 발급된 holdId를 바탕으로 현재 생성된 유효한 좌석 선점(Hold) 정보를 조회합니다.
     *
     * @param holdId 좌석 선점 고유 식별 UUID
     * @param claims 대기열 진입 토큰 정보
     * @return 좌석 선점 상세 정보
     */
    @Operation(summary = "좌석 선점 조회", description = "holdId로 현재 유효한 좌석 선점 정보를 조회합니다.")
    @GetMapping("/seats/holds/{holdId}")
    public ResponseEntity<ApiResponse<SeatHoldResponseDto>> getHold(
            @PathVariable String holdId,
            @Parameter(hidden = true)
            @RequestAttribute(EntryTokenRequestAttribute.CLAIMS) EntryTokenClaims claims
    ) {
        return ApiResponse.onSuccess(
                SuccessCode.OK,
                seatHoldService.get(holdId, claims)
        );
    }

    /**
     * 사용자가 예매 의사를 철회하거나 뒤로가기 등을 할 때, 생성된 좌석 선점을 즉시 만료시키고 해제합니다.
     *
     * @param holdId 좌석 선점 고유 식별 UUID
     * @param claims 대기열 진입 토큰 정보
     * @return 해제 처리된 좌석 선점 정보
     */
    @Operation(summary = "좌석 선점 해제", description = "현재 사용자가 생성한 좌석 선점을 즉시 해제합니다.")
    @DeleteMapping("/seats/holds/{holdId}")
    public ResponseEntity<ApiResponse<SeatHoldResponseDto>> releaseHold(
            @PathVariable String holdId,
            @Parameter(hidden = true)
            @RequestAttribute(EntryTokenRequestAttribute.CLAIMS) EntryTokenClaims claims
    ) {
        return ApiResponse.onSuccess(
                SuccessCode.OK,
                seatHoldService.release(holdId, claims)
        );
    }
}
