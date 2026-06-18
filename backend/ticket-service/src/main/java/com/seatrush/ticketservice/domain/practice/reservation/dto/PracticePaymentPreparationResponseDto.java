package com.seatrush.ticketservice.domain.practice.reservation.dto;

public record PracticePaymentPreparationResponseDto(
        String paymentId
) {

    public static PracticePaymentPreparationResponseDto ready(String paymentId) {
        return new PracticePaymentPreparationResponseDto(paymentId);
    }
}
