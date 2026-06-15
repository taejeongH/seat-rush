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

@Tag(name = "Payment", description = "Kafka로 생성된 Mock 결제 완료 API")
@SecurityRequirement(name = "bearerAuth")
@Validated
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Operation(
            summary = "결제 준비 상태 조회",
            description = "Kafka 결제 요청 이벤트가 소비되어 Mock 결제를 처리할 준비가 되었는지 조회합니다."
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

    @Operation(summary = "Mock 결제 완료", description = "결제를 성공 또는 실패 상태로 완료하고 결과 이벤트를 발행합니다.")
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
