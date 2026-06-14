package com.seatrush.ticketservice.domain.reservation.service;

import com.seatrush.ticketservice.common.exception.CustomException;
import com.seatrush.ticketservice.common.response.status.ErrorCode;
import com.seatrush.ticketservice.domain.reservation.entity.PaymentResultApplyResult;
import com.seatrush.ticketservice.domain.reservation.entity.Reservation;
import com.seatrush.ticketservice.domain.reservation.event.model.PaymentResultEvent;
import com.seatrush.ticketservice.domain.reservation.event.model.PaymentResultStatus;
import com.seatrush.ticketservice.domain.reservation.event.publisher.NotificationEventOutboxWriter;
import com.seatrush.ticketservice.domain.reservation.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PaymentResultService {

    private final ReservationRepository reservationRepository;
    private final ReservationHoldReleaseService holdReleaseService;
    private final NotificationEventOutboxWriter notificationEventOutboxWriter;

    public PaymentResultService(
            ReservationRepository reservationRepository,
            ReservationHoldReleaseService holdReleaseService,
            NotificationEventOutboxWriter notificationEventOutboxWriter
    ) {
        this.reservationRepository = reservationRepository;
        this.holdReleaseService = holdReleaseService;
        this.notificationEventOutboxWriter = notificationEventOutboxWriter;
    }

    /**
     * 결제 결과를 잠긴 예매에 멱등하게 반영하고 커밋 후 Redis hold를 제거합니다.
     */
    @Transactional
    public PaymentResultApplyResult apply(PaymentResultEvent event) {
        validateRequiredFields(event);
        Reservation reservation = reservationRepository
                .findByIdForPaymentResultUpdate(event.reservationId())
                .orElseThrow(() -> new CustomException(ErrorCode.RESERVATION_NOT_FOUND));
        validateEvent(reservation, event);

        PaymentResultApplyResult result;
        try {
            result = event.status() == PaymentResultStatus.SUCCESS
                    ? reservation.confirmPayment()
                    : reservation.failPayment();
        } catch (IllegalStateException exception) {
            throw new CustomException(ErrorCode.PAYMENT_RESULT_STATE_CONFLICT, exception);
        }

        if (result == PaymentResultApplyResult.APPLIED) {
            appendNotificationEvent(reservation, event.status());
        }
        holdReleaseService.releaseAfterCommit(reservation.getHoldId());
        return result;
    }

    private void appendNotificationEvent(
            Reservation reservation,
            PaymentResultStatus status
    ) {
        LocalDateTime occurredAt = LocalDateTime.now();
        if (status == PaymentResultStatus.SUCCESS) {
            notificationEventOutboxWriter.appendReservationConfirmed(
                    reservation,
                    occurredAt
            );
            return;
        }
        notificationEventOutboxWriter.appendPaymentFailed(
                reservation,
                occurredAt
        );
    }

    private void validateRequiredFields(PaymentResultEvent event) {
        if (event == null
                || event.eventId() == null
                || event.paymentId() == null
                || event.reservationId() == null
                || event.userId() == null
                || event.amount() == null
                || event.status() == null
                || event.occurredAt() == null) {
            throw new CustomException(ErrorCode.PAYMENT_RESULT_MISMATCH);
        }
    }

    private void validateEvent(
            Reservation reservation,
            PaymentResultEvent event
    ) {
        boolean mismatch = !event.paymentId().equals(reservation.getPaymentId())
                || !event.userId().equals(reservation.getUser().getId())
                || event.amount().compareTo(reservation.getTotalAmount()) != 0;
        if (mismatch) {
            throw new CustomException(ErrorCode.PAYMENT_RESULT_MISMATCH);
        }
    }
}
