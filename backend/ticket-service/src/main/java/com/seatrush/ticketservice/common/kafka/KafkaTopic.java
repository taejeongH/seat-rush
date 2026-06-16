package com.seatrush.ticketservice.common.kafka;

public final class KafkaTopic {

    public static final String SCHEDULE_STATUS = "schedule-status-v1";
    public static final String PAYMENT_REQUEST = "payment-request-v1";
    public static final String PAYMENT_RESULT = "payment-result-v1";
    public static final String PAYMENT_RESULT_DLT = PAYMENT_RESULT + ".DLT";
    public static final String RESERVATION_CONFIRMED = "reservation-confirmed-v1";
    public static final String PAYMENT_FAILED = "payment-failed-v1";
    public static final String ENTRY_SLOT_RELEASE = "entry-slot-release-v1";

    private KafkaTopic() {
    }
}
