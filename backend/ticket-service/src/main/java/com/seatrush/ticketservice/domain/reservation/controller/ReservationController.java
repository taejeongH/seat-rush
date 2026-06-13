package com.seatrush.ticketservice.domain.reservation.controller;

import com.seatrush.ticketservice.common.entrytoken.EntryTokenClaims;
import com.seatrush.ticketservice.common.entrytoken.EntryTokenRequestAttribute;
import com.seatrush.ticketservice.common.entrytoken.EntryTokenRequired;
import com.seatrush.ticketservice.common.response.ApiResponse;
import com.seatrush.ticketservice.common.response.status.SuccessCode;
import com.seatrush.ticketservice.domain.reservation.dto.request.ReservationCreateRequestDto;
import com.seatrush.ticketservice.domain.reservation.dto.response.ReservationResponseDto;
import com.seatrush.ticketservice.domain.reservation.service.ReservationFacade;
import com.seatrush.ticketservice.domain.reservation.service.ReservationService;
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
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Reservation", description = "예매 생성·조회·취소 API")
@SecurityRequirement(name = "bearerAuth")
@Validated
@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;
    private final ReservationFacade reservationFacade;

    public ReservationController(
            ReservationService reservationService,
            ReservationFacade reservationFacade
    ) {
        this.reservationService = reservationService;
        this.reservationFacade = reservationFacade;
    }

    @Operation(summary = "예매 생성", description = "유효한 좌석 선점을 결제 대기 예매로 전환합니다.")
    @SecurityRequirement(name = "entryToken")
    @EntryTokenRequired
    @PostMapping
    public ResponseEntity<ApiResponse<ReservationResponseDto>> create(
            @Parameter(hidden = true)
            @RequestAttribute(EntryTokenRequestAttribute.CLAIMS) EntryTokenClaims claims,
            @Valid @RequestBody ReservationCreateRequestDto request
    ) {
        return ApiResponse.onSuccess(
                SuccessCode.CREATED,
                reservationFacade.create(request.holdId(), claims)
        );
    }

    @Operation(summary = "예매 조회", description = "로그인 사용자가 소유한 예매 결과를 조회합니다.")
    @GetMapping("/{reservationId}")
    public ResponseEntity<ApiResponse<ReservationResponseDto>> get(
            @Positive @PathVariable Long reservationId,
            @Parameter(hidden = true)
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ApiResponse.onSuccess(
                SuccessCode.OK,
                reservationService.get(reservationId, userId)
        );
    }

    @Operation(summary = "예매 취소", description = "결제 대기 상태의 예매를 취소하고 좌석 선점을 해제합니다.")
    @DeleteMapping("/{reservationId}")
    public ResponseEntity<ApiResponse<ReservationResponseDto>> cancel(
            @Positive @PathVariable Long reservationId,
            @Parameter(hidden = true)
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ApiResponse.onSuccess(
                SuccessCode.OK,
                reservationService.cancel(reservationId, userId)
        );
    }
}
