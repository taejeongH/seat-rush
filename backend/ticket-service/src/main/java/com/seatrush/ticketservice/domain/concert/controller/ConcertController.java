package com.seatrush.ticketservice.domain.concert.controller;

import com.seatrush.ticketservice.common.response.ApiResponse;
import com.seatrush.ticketservice.common.response.PageResponseDto;
import com.seatrush.ticketservice.common.response.status.SuccessCode;
import com.seatrush.ticketservice.domain.concert.dto.response.ConcertDetailResponseDto;
import com.seatrush.ticketservice.domain.concert.dto.response.ConcertScheduleResponseDto;
import com.seatrush.ticketservice.domain.concert.dto.response.ConcertSummaryResponseDto;
import com.seatrush.ticketservice.domain.concert.service.ConcertQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 일반 사용자가 공연 목록을 둘러보고 특정 공연의 세부 사항 및 예매 가능한 회차(Schedule) 정보를 조회하는 조회 전용 컨트롤러입니다.
 */
@Tag(name = "Concert", description = "공연 및 회차 조회 API")
@RestController
@RequestMapping("/api/concerts")
public class ConcertController {

    private final ConcertQueryService concertQueryService;

    public ConcertController(ConcertQueryService concertQueryService) {
        this.concertQueryService = concertQueryService;
    }

    /**
     * 현재 등록된 모든 공연 리스트를 최근 등록순/최신순으로 페이징 조회합니다.
     *
     * @param page 조회할 페이지 번호 (0부터 시작)
     * @param size 한 페이지당 반환할 공연 건수
     * @return 페이징 처리된 공연 요약 목록 Dto
     */
    @Operation(summary = "공연 목록 조회", description = "등록된 공연을 최신순으로 페이지 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponseDto<ConcertSummaryResponseDto>>> getConcerts(
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기 (1~100)", example = "20")
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.onSuccess(SuccessCode.OK, concertQueryService.getConcerts(page, size));
    }

    /**
     * 특정 공연 식별자(concertId)에 해당하는 공연의 세부 소개, 장소, 시간 등 상세 프로필 정보를 조회합니다.
     *
     * @param concertId 공연 고유 식별 ID
     * @return 공연 상세 프로필 정보 Dto
     */
    @Operation(summary = "공연 상세 조회", description = "공연 ID로 공연 상세 정보를 조회합니다.")
    @GetMapping("/{concertId}")
    public ResponseEntity<ApiResponse<ConcertDetailResponseDto>> getConcert(
            @Parameter(description = "공연 ID", example = "1")
            @PathVariable Long concertId
    ) {
        return ApiResponse.onSuccess(SuccessCode.OK, concertQueryService.getConcert(concertId));
    }

    /**
     * 대상 공연에 매핑된 전체 공연 회차(공연 개시 일시, 예매 가능 상태 등) 목록을 날짜 오름차순으로 조회합니다.
     *
     * @param concertId 공연 고유 식별 ID
     * @return 공연 회차 목록 Dto
     */
    @Operation(summary = "회차 목록 조회", description = "공연에 등록된 회차를 공연 일시순으로 조회합니다.")
    @GetMapping("/{concertId}/schedules")
    public ResponseEntity<ApiResponse<List<ConcertScheduleResponseDto>>> getConcertSchedules(
            @Parameter(description = "공연 ID", example = "1")
            @PathVariable Long concertId
    ) {
        return ApiResponse.onSuccess(SuccessCode.OK, concertQueryService.getConcertSchedules(concertId));
    }
}

