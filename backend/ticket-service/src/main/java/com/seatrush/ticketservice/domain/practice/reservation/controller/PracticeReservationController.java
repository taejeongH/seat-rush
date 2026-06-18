package com.seatrush.ticketservice.domain.practice.reservation.controller;

import com.seatrush.ticketservice.common.entrytoken.EntryTokenClaims;
import com.seatrush.ticketservice.common.entrytoken.EntryTokenRequestAttribute;
import com.seatrush.ticketservice.common.entrytoken.EntryTokenRequired;
import com.seatrush.ticketservice.common.response.ApiResponse;
import com.seatrush.ticketservice.common.response.status.SuccessCode;
import com.seatrush.ticketservice.domain.practice.reservation.dto.PracticePaymentCompleteRequestDto;
import com.seatrush.ticketservice.domain.practice.reservation.dto.PracticePaymentPreparationResponseDto;
import com.seatrush.ticketservice.domain.practice.reservation.dto.PracticePaymentResponseDto;
import com.seatrush.ticketservice.domain.practice.reservation.service.PracticeReservationService;
import com.seatrush.ticketservice.domain.reservation.dto.request.ReservationCreateRequestDto;
import com.seatrush.ticketservice.domain.reservation.dto.response.PaymentRequestResponseDto;
import com.seatrush.ticketservice.domain.reservation.dto.response.ReservationResponseDto;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 연습 모드(Practice Mode)에서의 가상 예매 및 결제 흐름 처리를 위한 컨트롤러 클래스입니다.
 * 
 * 실제 DB 영속성 없이 Redis 인메모리 데이터를 기반으로 예매 시뮬레이션을 수행하므로,
 * 응답 지연이 최소화되어 대량의 가상 요청을 원활하게 테스트할 수 있습니다.
 */
@Validated
@RestController
@RequestMapping("/api/practice-reservations")
public class PracticeReservationController {

    private final PracticeReservationService practiceReservationService;

    public PracticeReservationController(PracticeReservationService practiceReservationService) {
        this.practiceReservationService = practiceReservationService;
    }

    /**
     * 연습 모드에서 사용자의 가상 예매 데이터를 생성합니다.
     * 대기열 통과 정보인 EntryToken 검증이 사전 요구됩니다.
     *
     * @param claims 요청 속성에서 추출한 검증 완료된 대기열 토큰 정보
     * @param request 가상 좌석 선점 식별 키(holdId)를 담은 요청 Dto
     * @return 가상 예매 생성 상세 결과 Dto
     */
    @EntryTokenRequired
    @PostMapping("/reservations")
    public ResponseEntity<ApiResponse<ReservationResponseDto>> createReservation(
            @RequestAttribute(EntryTokenRequestAttribute.CLAIMS) EntryTokenClaims claims,
            @Valid @RequestBody ReservationCreateRequestDto request
    ) {
        return ApiResponse.onSuccess(
                SuccessCode.CREATED,
                practiceReservationService.create(request.holdId(), claims)
        );
    }

    /**
     * 연습 모드 가상 세션에 종속된 특정 가상 예매 정보를 조회합니다.
     *
     * @param practiceSessionId 고유 연습 세션 식별 ID
     * @param reservationId 가상 예매 ID
     * @param userId API 게이트웨이로부터 전달받은 사용자 식별자
     * @return 가상 예매 정보 조회 결과 Dto
     */
    @GetMapping("/sessions/{practiceSessionId}/reservations/{reservationId}")
    public ResponseEntity<ApiResponse<ReservationResponseDto>> getReservation(
            @PathVariable String practiceSessionId,
            @Positive @PathVariable Long reservationId,
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ApiResponse.onSuccess(
                SuccessCode.OK,
                practiceReservationService.get(practiceSessionId, reservationId, userId)
        );
    }

    /**
     * 특정 가상 예매 건에 대해 PG 결제 페이지 진입 단계(PAYMENT_PROCESSING)로 상태를 변경합니다.
     *
     * @param practiceSessionId 고유 연습 세션 식별 ID
     * @param reservationId 가상 예매 ID
     * @param userId API 게이트웨이로부터 전달받은 사용자 식별자
     * @return 결제 고유 식별값(paymentId)이 포함된 결제 준비 완료 응답 Dto
     */
    @PostMapping("/sessions/{practiceSessionId}/reservations/{reservationId}/payments")
    public ResponseEntity<ApiResponse<PaymentRequestResponseDto>> requestPayment(
            @PathVariable String practiceSessionId,
            @Positive @PathVariable Long reservationId,
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ApiResponse.onSuccess(
                SuccessCode.ACCEPTED,
                practiceReservationService.requestPayment(practiceSessionId, reservationId, userId)
        );
    }

    /**
     * 외부 가상 PG 페이지 진입 시 결제 정보를 매칭 조회하기 위한 준비 API입니다.
     *
     * @param practiceSessionId 고유 연습 세션 식별 ID
     * @param paymentId 결제 고유 식별 ID
     * @param userId API 게이트웨이로부터 전달받은 사용자 식별자
     * @return 결제 페이지 진입 준비 응답 Dto
     */
    @GetMapping("/sessions/{practiceSessionId}/payments/{paymentId}")
    public ResponseEntity<ApiResponse<PracticePaymentPreparationResponseDto>> getPayment(
            @PathVariable String practiceSessionId,
            @PathVariable String paymentId,
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ApiResponse.onSuccess(
                SuccessCode.OK,
                practiceReservationService.getPayment(practiceSessionId, paymentId, userId)
        );
    }

    /**
     * 가상 PG 화면에서 사용자가 최종 결제 승인/취소 버튼을 클릭 시 결제 결과를 확정(CONFIRMED/CANCELED) 처리합니다.
     *
     * @param practiceSessionId 고유 연습 세션 식별 ID
     * @param paymentId 결제 고유 식별 ID
     * @param userId API 게이트웨이로부터 전달받은 사용자 식별자
     * @param request 사용자의 결제 승인/실패(SUCCESS/FAIL) 코드를 담은 요청 Dto
     * @return 시뮬레이터 결제 처리 최종 결과 Dto
     */
    @PostMapping("/sessions/{practiceSessionId}/payments/{paymentId}/complete")
    public ResponseEntity<ApiResponse<PracticePaymentResponseDto>> completePayment(
            @PathVariable String practiceSessionId,
            @PathVariable String paymentId,
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody PracticePaymentCompleteRequestDto request
    ) {
        return ApiResponse.onSuccess(
                SuccessCode.OK,
                practiceReservationService.completePayment(
                        practiceSessionId,
                        paymentId,
                        userId,
                        request.result()
                )
        );
    }

    /**
     * 연습 세션이 종료되었거나 예매 연습을 초기화(리셋)할 때, Redis 내 해당 세션 키들을 일괄 삭제합니다.
     *
     * @param practiceSessionId 삭제할 대상 고유 연습 세션 ID
     * @return 성공 여부 응답
     */
    @DeleteMapping("/sessions/{practiceSessionId}")
    public ResponseEntity<ApiResponse<Void>> deleteSession(
            @PathVariable String practiceSessionId
    ) {
        practiceReservationService.deleteSession(practiceSessionId);
        return ApiResponse.onSuccess(SuccessCode.OK, null);
    }
}
