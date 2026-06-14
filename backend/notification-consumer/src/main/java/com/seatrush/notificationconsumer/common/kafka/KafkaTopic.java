package com.seatrush.notificationconsumer.common.kafka;

public final class KafkaTopic {

    public static final String RESERVATION_CONFIRMED = "reservation-confirmed-v1";
    public static final String RESERVATION_CONFIRMED_DLT =
            RESERVATION_CONFIRMED + ".DLT";
    public static final String PAYMENT_FAILED = "payment-failed-v1";
    public static final String PAYMENT_FAILED_DLT = PAYMENT_FAILED + ".DLT";

    private KafkaTopic() {
    }
}
