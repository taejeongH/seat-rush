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

@Tag(name = "Concert", description = "공연 및 회차 조회 API")
@RestController
@RequestMapping("/api/concerts")
public class ConcertController {

    private final ConcertQueryService concertQueryService;

    public ConcertController(ConcertQueryService concertQueryService) {
        this.concertQueryService = concertQueryService;
    }

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

    @Operation(summary = "공연 상세 조회", description = "공연 ID로 공연 상세 정보를 조회합니다.")
    @GetMapping("/{concertId}")
    public ResponseEntity<ApiResponse<ConcertDetailResponseDto>> getConcert(
            @Parameter(description = "공연 ID", example = "1")
            @PathVariable Long concertId
    ) {
        return ApiResponse.onSuccess(SuccessCode.OK, concertQueryService.getConcert(concertId));
    }

    @Operation(summary = "회차 목록 조회", description = "공연에 등록된 회차를 공연 일시순으로 조회합니다.")
    @GetMapping("/{concertId}/schedules")
    public ResponseEntity<ApiResponse<List<ConcertScheduleResponseDto>>> getConcertSchedules(
            @Parameter(description = "공연 ID", example = "1")
            @PathVariable Long concertId
    ) {
        return ApiResponse.onSuccess(SuccessCode.OK, concertQueryService.getConcertSchedules(concertId));
    }
}
