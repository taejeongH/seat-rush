package com.seatrush.paymentservice.common.kafka;

public final class KafkaTopic {

    public static final String PAYMENT_REQUEST = "payment-request-v1";
    public static final String PAYMENT_REQUEST_DLT = PAYMENT_REQUEST + ".DLT";
    public static final String PAYMENT_RESULT = "payment-result-v1";

    private KafkaTopic() {
    }
}
