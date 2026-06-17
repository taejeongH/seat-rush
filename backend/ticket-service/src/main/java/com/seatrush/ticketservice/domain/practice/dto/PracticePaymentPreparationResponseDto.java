package com.seatrush.ticketservice.domain.practice.dto;

public record PracticePaymentPreparationResponseDto(
        String paymentId,
        String status
) {

    public static PracticePaymentPreparationResponseDto ready(String paymentId) {
        return new PracticePaymentPreparationResponseDto(paymentId, "READY");
    }
}
