package com.seatrush.ticketservice.domain.reservation.entity;

public enum ReservationStatus {
    PENDING_PAYMENT,
    PAYMENT_PROCESSING,
    CONFIRMED,
    CANCELED,
    EXPIRED
}
