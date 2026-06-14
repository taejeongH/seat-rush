package com.seatrush.ticketservice.domain.reservation.dto.response;

import com.seatrush.ticketservice.domain.reservation.entity.Reservation;
import com.seatrush.ticketservice.domain.reservation.entity.ReservationStatus;

public record PaymentRequestResponseDto(
        Long reservationId,
        String paymentId,
        ReservationStatus status
) {

    public static PaymentRequestResponseDto from(Reservation reservation) {
        return new PaymentRequestResponseDto(
                reservation.getId(),
                reservation.getPaymentId(),
                reservation.getStatus()
        );
    }
}
