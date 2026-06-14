package com.seatrush.ticketservice.domain.reservation.service;

import com.seatrush.ticketservice.common.exception.CustomException;
import com.seatrush.ticketservice.common.response.status.ErrorCode;
import com.seatrush.ticketservice.domain.reservation.entity.PaymentResultApplyResult;
import com.seatrush.ticketservice.domain.reservation.entity.Reservation;
import com.seatrush.ticketservice.domain.reservation.event.model.PaymentResultEvent;
import com.seatrush.ticketservice.domain.reservation.event.model.PaymentResultStatus;
import com.seatrush.ticketservice.domain.reservation.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentResultService {

    private final ReservationRepository reservationRepository;
    private final ReservationHoldReleaseService holdReleaseService;

    public PaymentResultService(
            ReservationRepository reservationRepository,
            ReservationHoldReleaseService holdReleaseService
    ) {
        this.reservationRepository = reservationRepository;
        this.holdReleaseService = holdReleaseService;
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

        holdReleaseService.releaseAfterCommit(reservation.getHoldId());
        return result;
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
