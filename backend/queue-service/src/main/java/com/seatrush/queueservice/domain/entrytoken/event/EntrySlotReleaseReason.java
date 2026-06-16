package com.seatrush.queueservice.domain.entrytoken.event;

public enum EntrySlotReleaseReason {
    PAYMENT_SUCCESS,
    PAYMENT_FAILED,
    RESERVATION_CANCELED,
    RESERVATION_EXPIRED
}
