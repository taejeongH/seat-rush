package com.seatrush.paymentservice.domain.payment.dto.response;

import com.seatrush.paymentservice.domain.payment.entity.PaymentStatus;

public enum PaymentPreparationStatus {
    PROCESSING,
    READY,
    SUCCESS,
    FAILED;

    public static PaymentPreparationStatus from(PaymentStatus status) {
        return switch (status) {
            case PENDING -> READY;
            case SUCCESS -> SUCCESS;
            case FAILED -> FAILED;
        };
    }
}
