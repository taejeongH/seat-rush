package com.seatrush.ticketservice.domain.seatlayout.controller;

import com.seatrush.ticketservice.common.entrytoken.EntryTokenClaims;
import com.seatrush.ticketservice.common.entrytoken.EntryTokenRequestAttribute;
import com.seatrush.ticketservice.common.entrytoken.EntryTokenRequired;
import com.seatrush.ticketservice.common.response.ApiResponse;
import com.seatrush.ticketservice.common.response.status.SuccessCode;
import com.seatrush.ticketservice.domain.seatlayout.dto.response.SeatLayoutResponseDto;
import com.seatrush.ticketservice.domain.seatlayout.dto.response.SeatLayoutSeatResponseDto;
import com.seatrush.ticketservice.domain.seatlayout.dto.response.SeatLayoutSectionResponseDto;
import com.seatrush.ticketservice.domain.seatlayout.service.SeatLayoutQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Seat Layout", description = "연습 모드 좌석 배치 API")
@Validated
@RestController
@RequestMapping("/api")
public class SeatLayoutController {

    private final SeatLayoutQueryService seatLayoutQueryService;

    public SeatLayoutController(SeatLayoutQueryService seatLayoutQueryService) {
        this.seatLayoutQueryService = seatLayoutQueryService;
    }

    @Operation(summary = "좌석 배치 목록 조회", description = "연습 모드에서 선택할 수 있는 좌석 배치 템플릿을 조회합니다.")
    @GetMapping("/seat-layouts")
    public ResponseEntity<ApiResponse<List<SeatLayoutResponseDto>>> getLayouts() {
        return ApiResponse.onSuccess(
                SuccessCode.OK,
                seatLayoutQueryService.getLayouts()
        );
    }

    @EntryTokenRequired
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "entryToken")
    @Operation(summary = "연습 좌석 구역 조회", description = "연습 세션의 좌석 배치 구역을 조회합니다.")
    @GetMapping("/practice/sessions/{practiceSessionId}/seat-layouts/{seatLayoutId}/sections")
    public ResponseEntity<ApiResponse<List<SeatLayoutSectionResponseDto>>> getPracticeSections(
            @PathVariable String practiceSessionId,
            @Positive @PathVariable Long seatLayoutId,
            @Parameter(hidden = true)
            @RequestAttribute(EntryTokenRequestAttribute.CLAIMS) EntryTokenClaims claims
    ) {
        return ApiResponse.onSuccess(
                SuccessCode.OK,
                seatLayoutQueryService.getPracticeSections(seatLayoutId, practiceSessionId, claims)
        );
    }

    @EntryTokenRequired
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "entryToken")
    @Operation(summary = "연습 좌석 목록 조회", description = "연습 세션의 좌석 상태를 Redis 선점 상태와 함께 조회합니다.")
    @GetMapping("/practice/sessions/{practiceSessionId}/seat-layouts/{seatLayoutId}/seats")
    public ResponseEntity<ApiResponse<List<SeatLayoutSeatResponseDto>>> getPracticeSeats(
            @PathVariable String practiceSessionId,
            @Positive @PathVariable Long seatLayoutId,
            @Positive @RequestParam Long sectionId,
            @Parameter(hidden = true)
            @RequestAttribute(EntryTokenRequestAttribute.CLAIMS) EntryTokenClaims claims
    ) {
        return ApiResponse.onSuccess(
                SuccessCode.OK,
                seatLayoutQueryService.getPracticeSeats(
                        seatLayoutId,
                        sectionId,
                        practiceSessionId,
                        claims
                )
        );
    }
}
