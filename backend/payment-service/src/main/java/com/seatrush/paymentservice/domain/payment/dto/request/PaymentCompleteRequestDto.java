package com.seatrush.paymentservice.domain.payment.dto.request;

import com.seatrush.paymentservice.domain.payment.entity.PaymentStatus;
import jakarta.validation.constraints.NotNull;

public record PaymentCompleteRequestDto(
        @NotNull
        PaymentStatus result
) {
}
