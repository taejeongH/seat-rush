package com.seatrush.ticketservice.common.kafka;

public final class KafkaTopic {

    public static final String SCHEDULE_STATUS = "schedule-status-v1";
    public static final String PAYMENT_REQUEST = "payment-request-v1";
    public static final String PAYMENT_RESULT = "payment-result-v1";
    public static final String PAYMENT_RESULT_DLT = PAYMENT_RESULT + ".DLT";

    private KafkaTopic() {
    }
}
