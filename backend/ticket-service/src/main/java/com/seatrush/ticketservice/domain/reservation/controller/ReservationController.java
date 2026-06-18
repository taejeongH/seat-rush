package com.seatrush.ticketservice.domain.reservation.controller;

import com.seatrush.ticketservice.common.entrytoken.EntryTokenClaims;
import com.seatrush.ticketservice.common.entrytoken.EntryTokenRequestAttribute;
import com.seatrush.ticketservice.common.entrytoken.EntryTokenRequired;
import com.seatrush.ticketservice.common.response.ApiResponse;
import com.seatrush.ticketservice.common.response.status.SuccessCode;
import com.seatrush.ticketservice.domain.reservation.dto.request.ReservationCreateRequestDto;
import com.seatrush.ticketservice.domain.reservation.dto.response.ReservationResponseDto;
import com.seatrush.ticketservice.domain.reservation.dto.response.PaymentRequestResponseDto;
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

/**
 * 예매(Reservation)의 결제 대기 생성, 상태 조회, 수동 취소 및 결제 요청을 관장하는 컨트롤러입니다.
 */
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

    /**
     * 사용자가 임시 선점한 좌석 정보(holdId)를 기반으로 공식 결제 대기(Pending Payment) 상태의 예매를 생성합니다.
     * 대기열 진입 토큰(Entry Token)의 유효성을 검사합니다.
     *
     * @param claims 대기열 진입 토큰 정보
     * @param request holdId가 포함된 생성 요청 바디 Dto
     * @return 생성된 예매 정보 Dto
     */
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

    /**
     * 특정 예매 상세 내용을 조회합니다.
     * 예매 시점 기한이 지난 경우 내부적으로 예매 만료(Expired) 상태로의 전이 처리가 수행됩니다.
     *
     * @param reservationId 예매 고유 식별 ID
     * @param userId Gateway 헤더로부터 획득한 사용자 ID
     * @return 예매 상세 정보 Dto
     */
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

    /**
     * 사용자가 아직 결제되지 않은 예매를 직접 수동 취소합니다.
     * 연관되어 선점되었던 Redis 좌석 정보(Hold)도 트랜잭션 정상 커밋 후 비동기로 일제 해제됩니다.
     *
     * @param reservationId 예매 고유 식별 ID
     * @param userId Gateway 헤더로부터 획득한 사용자 ID
     * @return 취소 완료된 예매 정보 Dto
     */
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

    /**
     * 결제 대기 중인 특정 예매 건에 대해 최종 결제 연동(외부 PG 연동 등)을 요청합니다.
     * 예매 상태를 결제 처리 중(PAYING) 상태로 전환하고 비동기 카프카 결제 요청 아웃박스 레코드를 추가합니다.
     *
     * @param reservationId 예매 고유 식별 ID
     * @param userId Gateway 헤더로부터 획득한 사용자 ID
     * @return 결제 요청 접수 완료 결과 Dto
     */
    @Operation(
            summary = "결제 요청",
            description = "예매를 결제 처리 중 상태로 전환하고 비동기 결제 요청을 접수합니다."
    )
    @PostMapping("/{reservationId}/payments")
    public ResponseEntity<ApiResponse<PaymentRequestResponseDto>> requestPayment(
            @Positive @PathVariable Long reservationId,
            @Parameter(hidden = true)
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ApiResponse.onSuccess(
                SuccessCode.ACCEPTED,
                reservationService.requestPayment(reservationId, userId)
        );
    }
}

