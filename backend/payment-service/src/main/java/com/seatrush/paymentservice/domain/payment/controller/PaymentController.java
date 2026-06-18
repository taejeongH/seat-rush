package com.seatrush.paymentservice.domain.payment.controller;

import com.seatrush.paymentservice.common.response.ApiResponse;
import com.seatrush.paymentservice.common.response.status.SuccessCode;
import com.seatrush.paymentservice.domain.payment.dto.request.PaymentCompleteRequestDto;
import com.seatrush.paymentservice.domain.payment.dto.response.PaymentPreparationResponseDto;
import com.seatrush.paymentservice.domain.payment.dto.response.PaymentResponseDto;
import com.seatrush.paymentservice.domain.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mock 결제 준비 상태 조회와 결제 완료 요청 API를 제공합니다.
 *
 * 결제 요청 생성은 Ticket Service의 Kafka 이벤트를 통해 비동기로 처리됩니다.
 */
@Tag(name = "Payment")
@SecurityRequirement(name = "bearerAuth")
@Validated
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * paymentId에 해당하는 결제 데이터가 준비되었는지 확인합니다.
     */
    @Operation(
            summary = "결제 준비 상태 조회",
            description = "Kafka 결제 요청 이벤트 소비가 아직 끝나지 않았으면 PROCESSING 상태를 반환합니다."
    )
    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentPreparationResponseDto>> getPreparationStatus(
            @PathVariable String paymentId,
            @Parameter(hidden = true)
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ApiResponse.onSuccess(
                SuccessCode.OK,
                paymentService.getPreparationStatus(paymentId, userId)
        );
    }

    /**
     * Mock 결제 성공 또는 실패 결과를 반영합니다.
     */
    @Operation(
            summary = "결제 성공/실패 Mock",
            description = "결제 결과를 저장하고 결제 결과 이벤트를 Outbox에 기록합니다."
    )
    @PostMapping("/{paymentId}/complete")
    public ResponseEntity<ApiResponse<PaymentResponseDto>> complete(
            @PathVariable String paymentId,
            @Parameter(hidden = true)
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody PaymentCompleteRequestDto request
    ) {
        return ApiResponse.onSuccess(
                SuccessCode.OK,
                paymentService.complete(paymentId, userId, request.result())
        );
    }
}
