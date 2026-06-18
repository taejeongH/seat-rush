package com.seatrush.ticketservice.domain.seatlayout.controller;

import com.seatrush.ticketservice.common.entrytoken.EntryTokenClaims;
import com.seatrush.ticketservice.common.entrytoken.EntryTokenRequestAttribute;
import com.seatrush.ticketservice.common.entrytoken.EntryTokenRequired;
import com.seatrush.ticketservice.common.response.ApiResponse;
import com.seatrush.ticketservice.common.response.status.SuccessCode;
import com.seatrush.ticketservice.domain.seatlayout.dto.response.SeatLayoutResponseDto;
import com.seatrush.ticketservice.domain.seatlayout.dto.response.SeatLayoutSeatResponseDto;
import com.seatrush.ticketservice.domain.seatlayout.dto.response.SeatLayoutSectionResponseDto;
import com.seatrush.ticketservice.domain.seat.service.SeatQueryService;
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

/**
 * 연습 모드용 좌석 배치(Seat Layout), 구역(Section), 및 개별 좌석 상태를 조회하는 컨트롤러입니다.
 * 
 * 특정 대기열 통과 후에 발급받는 대기열 진입 토큰(Entry Token) 검증이 연동되어 있습니다.
 */
@Tag(name = "Seat Layout", description = "연습 모드 좌석 배치 API")
@Validated
@RestController
@RequestMapping("/api")
public class SeatLayoutController {

    private final SeatLayoutQueryService seatLayoutQueryService;
    private final SeatQueryService seatQueryService;

    public SeatLayoutController(
            SeatLayoutQueryService seatLayoutQueryService,
            SeatQueryService seatQueryService
    ) {
        this.seatLayoutQueryService = seatLayoutQueryService;
        this.seatQueryService = seatQueryService;
    }

    /**
     * 연습 모드 진입 시 사용자가 선택할 수 있는 전체 좌석 배치 템플릿(예: 올림픽공원 체조경기장 등) 목록을 조회합니다.
     *
     * @return 좌석 배치 템플릿 목록
     */
    @Operation(summary = "좌석 배치 목록 조회", description = "연습 모드에서 선택할 수 있는 좌석 배치 템플릿을 조회합니다.")
    @GetMapping("/seat-layouts")
    public ResponseEntity<ApiResponse<List<SeatLayoutResponseDto>>> getLayouts() {
        return ApiResponse.onSuccess(
                SuccessCode.OK,
                seatLayoutQueryService.getLayouts()
        );
    }

    /**
     * 진행 중인 특정 연습 세션과 연계된 좌석 배치의 각 구역(Section) 정보 목록을 조회합니다.
     * {@link EntryTokenRequired} 인터셉터를 통과하여 검증된 클레임 정보를 활용합니다.
     *
     * @param practiceSessionId 활성화된 연습 세션 고유 식별자 UUID
     * @param seatLayoutId 조회 대상 좌석 배치 식별자 ID
     * @param claims 인터셉터에서 주입한 대기열 진입 토큰 클레임 세트
     * @return 좌석 구역 목록
     */
    @EntryTokenRequired
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "entryToken")
    @Operation(summary = "연습 좌석 구역 조회", description = "연습 세션의 좌석 배치 구역을 조회합니다.")
    @GetMapping("/practice-reservations/sessions/{practiceSessionId}/seat-layouts/{seatLayoutId}/sections")
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

    /**
     * 특정 연습 세션 및 구역에 속한 개별 좌석들의 전체 목록 및 현재 Redis 선점 여부 상태를 함께 조회합니다.
     * {@link EntryTokenRequired} 인터셉터를 통해 토큰의 세션 매칭 여부를 검증합니다.
     *
     * @param practiceSessionId 활성화된 연습 세션 고유 식별자 UUID
     * @param seatLayoutId 조회 대상 좌석 배치 식별자 ID
     * @param sectionId 조회 대상 좌석 구역 식별자 ID
     * @param claims 인터셉터에서 주입한 대기열 진입 토큰 클레임 세트
     * @return 실시간 상태가 반영된 좌석 목록
     */
    @EntryTokenRequired
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "entryToken")
    @Operation(summary = "연습 좌석 목록 조회", description = "연습 세션의 좌석 상태를 Redis 선점 상태와 함께 조회합니다.")
    @GetMapping("/practice-reservations/sessions/{practiceSessionId}/seat-layouts/{seatLayoutId}/seats")
    public ResponseEntity<ApiResponse<List<SeatLayoutSeatResponseDto>>> getPracticeSeats(
            @PathVariable String practiceSessionId,
            @Positive @PathVariable Long seatLayoutId,
            @Positive @RequestParam Long sectionId,
            @Parameter(hidden = true)
            @RequestAttribute(EntryTokenRequestAttribute.CLAIMS) EntryTokenClaims claims
    ) {
        return ApiResponse.onSuccess(
                SuccessCode.OK,
                seatQueryService.getPracticeSeats(
                        seatLayoutId,
                        sectionId,
                        practiceSessionId,
                        claims
                )
        );
    }
}

