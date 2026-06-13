package com.seatrush.ticketservice.domain.seat.controller;

import com.seatrush.ticketservice.common.entrytoken.EntryTokenClaims;
import com.seatrush.ticketservice.common.entrytoken.EntryTokenRequestAttribute;
import com.seatrush.ticketservice.common.entrytoken.EntryTokenRequired;
import com.seatrush.ticketservice.common.response.ApiResponse;
import com.seatrush.ticketservice.common.response.status.SuccessCode;
import com.seatrush.ticketservice.domain.seat.dto.request.SeatHoldRequestDto;
import com.seatrush.ticketservice.domain.seat.dto.response.SeatHoldResponseDto;
import com.seatrush.ticketservice.domain.seat.dto.response.SeatResponseDto;
import com.seatrush.ticketservice.domain.seat.dto.response.SeatSectionResponseDto;
import com.seatrush.ticketservice.domain.seat.service.SeatHoldService;
import com.seatrush.ticketservice.domain.seat.service.SeatQueryService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Seat", description = "좌석 조회 및 선점 API")
@SecurityRequirement(name = "bearerAuth")
@SecurityRequirement(name = "entryToken")
@EntryTokenRequired
@Validated
@RestController
@RequestMapping("/api")
public class SeatController {

    private final SeatQueryService seatQueryService;
    private final SeatHoldService seatHoldService;

    public SeatController(
            SeatQueryService seatQueryService,
            SeatHoldService seatHoldService
    ) {
        this.seatQueryService = seatQueryService;
        this.seatHoldService = seatHoldService;
    }

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
