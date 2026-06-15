package com.seatrush.paymentservice.domain.payment.dto.response;

import com.seatrush.paymentservice.domain.payment.entity.Payment;

public record PaymentPreparationResponseDto(
        String paymentId,
        PaymentPreparationStatus status
) {

    public static PaymentPreparationResponseDto processing(String paymentId) {
        return new PaymentPreparationResponseDto(
                paymentId,
                PaymentPreparationStatus.PROCESSING
        );
    }

    public static PaymentPreparationResponseDto from(Payment payment) {
        return new PaymentPreparationResponseDto(
                payment.getId(),
                PaymentPreparationStatus.from(payment.getStatus())
        );
    }
}
