package com.seatrush.ticketservice.domain.reservation.event.model;

public enum EntrySlotReleaseReason {
    PAYMENT_SUCCESS,
    PAYMENT_FAILED,
    RESERVATION_CANCELED,
    RESERVATION_EXPIRED
}
