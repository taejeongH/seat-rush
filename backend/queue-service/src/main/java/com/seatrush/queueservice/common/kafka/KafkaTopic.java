package com.seatrush.queueservice.common.kafka;

public final class KafkaTopic {

    public static final String SCHEDULE_STATUS = "schedule-status-v1";
    public static final String SCHEDULE_STATUS_DLT = SCHEDULE_STATUS + ".DLT";
    public static final String ENTRY_SLOT_RELEASE = "entry-slot-release-v1";
    public static final String ENTRY_SLOT_RELEASE_DLT = ENTRY_SLOT_RELEASE + ".DLT";

    private KafkaTopic() {
    }
}
