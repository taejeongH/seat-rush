package com.seatrush.ticketservice.domain.reservation.service;

import com.seatrush.ticketservice.common.exception.CustomException;
import com.seatrush.ticketservice.common.response.status.ErrorCode;
import com.seatrush.ticketservice.domain.auth.entity.User;
import com.seatrush.ticketservice.domain.concert.entity.ConcertSchedule;
import com.seatrush.ticketservice.domain.reservation.entity.PaymentResultApplyResult;
import com.seatrush.ticketservice.domain.reservation.entity.Reservation;
import com.seatrush.ticketservice.domain.reservation.entity.ReservationStatus;
import com.seatrush.ticketservice.domain.reservation.event.model.PaymentResultEvent;
import com.seatrush.ticketservice.domain.reservation.event.model.PaymentResultStatus;
import com.seatrush.ticketservice.domain.reservation.repository.ReservationRepository;
import com.seatrush.ticketservice.domain.seat.entity.Seat;
import com.seatrush.ticketservice.domain.seat.entity.SeatSection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 결제 결과에 따른 예매·좌석 상태 반영과 충돌 정책을 검증합니다.
 */
class PaymentResultServiceTest {

    private ReservationRepository reservationRepository;
    private ReservationHoldReleaseService holdReleaseService;
    private PaymentResultService service;

    @BeforeEach
    void setUp() {
        reservationRepository = mock(ReservationRepository.class);
        holdReleaseService = mock(ReservationHoldReleaseService.class);
        service = new PaymentResultService(
                reservationRepository,
                holdReleaseService
        );
    }

    /**
     * 결제 성공을 예매와 좌석에 반영하고 Redis hold 해제를 등록합니다.
     */
    @Test
    void applyPaymentSuccess() {
        Seat seat = seat();
        Reservation reservation = processingReservation(seat);
        when(reservationRepository.findByIdForPaymentResultUpdate(100L))
                .thenReturn(Optional.of(reservation));

        PaymentResultApplyResult result =
                service.apply(event(PaymentResultStatus.SUCCESS));

        assertThat(result).isEqualTo(PaymentResultApplyResult.APPLIED);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        verify(seat).reserve();
        verify(holdReleaseService).releaseAfterCommit("hold-1");
    }

    /**
     * 결제 실패를 예매 취소로 반영하고 Redis hold 해제를 등록합니다.
     */
    @Test
    void applyPaymentFailure() {
        Reservation reservation = processingReservation(seat());
        when(reservationRepository.findByIdForPaymentResultUpdate(100L))
                .thenReturn(Optional.of(reservation));

        PaymentResultApplyResult result =
                service.apply(event(PaymentResultStatus.FAILED));

        assertThat(result).isEqualTo(PaymentResultApplyResult.APPLIED);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELED);
        verify(holdReleaseService).releaseAfterCommit("hold-1");
    }

    /**
     * 같은 결과를 다시 소비하면 상태는 유지하고 Redis hold 삭제를 재시도합니다.
     */
    @Test
    void ignoreDuplicatePaymentResult() {
        Reservation reservation = processingReservation(seat());
        reservation.confirmPayment();
        when(reservationRepository.findByIdForPaymentResultUpdate(100L))
                .thenReturn(Optional.of(reservation));

        PaymentResultApplyResult result =
                service.apply(event(PaymentResultStatus.SUCCESS));

        assertThat(result).isEqualTo(PaymentResultApplyResult.DUPLICATE);
        verify(holdReleaseService).releaseAfterCommit("hold-1");
    }

    /**
     * 결제 요청 후 기존 만료 시각이 지나도 처리 중인 성공 결과는 반영합니다.
     */
    @Test
    void acceptSuccessAfterOriginalExpirationWhileProcessing() {
        Reservation reservation = processingReservation(seat());
        ReflectionTestUtils.setField(
                reservation,
                "expiresAt",
                LocalDateTime.now().minusSeconds(1)
        );
        when(reservationRepository.findByIdForPaymentResultUpdate(100L))
                .thenReturn(Optional.of(reservation));

        PaymentResultApplyResult result =
                service.apply(event(PaymentResultStatus.SUCCESS));

        assertThat(result).isEqualTo(PaymentResultApplyResult.APPLIED);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    /**
     * 이미 만료된 예매를 성공 결과로 되살리는 상태 역전을 거부합니다.
     */
    @Test
    void rejectSuccessForExpiredReservation() {
        Reservation reservation = processingReservation(seat());
        ReflectionTestUtils.setField(reservation, "status", ReservationStatus.EXPIRED);
        when(reservationRepository.findByIdForPaymentResultUpdate(100L))
                .thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.apply(event(PaymentResultStatus.SUCCESS)))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_RESULT_STATE_CONFLICT);
    }

    /**
     * 결제 식별자나 금액이 다른 이벤트는 예매에 반영하지 않습니다.
     */
    @Test
    void rejectMismatchedPaymentResult() {
        Reservation reservation = processingReservation(seat());
        when(reservationRepository.findByIdForPaymentResultUpdate(100L))
                .thenReturn(Optional.of(reservation));
        PaymentResultEvent event = new PaymentResultEvent(
                UUID.randomUUID(),
                "other-payment",
                100L,
                10L,
                new BigDecimal("150000"),
                PaymentResultStatus.SUCCESS,
                LocalDateTime.now()
        );

        assertThatThrownBy(() -> service.apply(event))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_RESULT_MISMATCH);
    }

    private Reservation processingReservation(Seat seat) {
        User user = mock(User.class);
        ConcertSchedule schedule = seat.getSection().getSchedule();
        when(user.getId()).thenReturn(10L);
        Reservation reservation = Reservation.create(
                user,
                schedule,
                "hold-1",
                List.of(seat),
                LocalDateTime.now().plusMinutes(10)
        );
        ReflectionTestUtils.setField(reservation, "id", 100L);
        reservation.requestPayment("payment-1", LocalDateTime.now());
        return reservation;
    }

    private PaymentResultEvent event(PaymentResultStatus status) {
        return new PaymentResultEvent(
                UUID.randomUUID(),
                "payment-1",
                100L,
                10L,
                new BigDecimal("150000"),
                status,
                LocalDateTime.now()
        );
    }

    private Seat seat() {
        ConcertSchedule schedule = mock(ConcertSchedule.class);
        SeatSection section = mock(SeatSection.class);
        Seat seat = mock(Seat.class);
        when(section.getSchedule()).thenReturn(schedule);
        when(section.getPrice()).thenReturn(new BigDecimal("150000"));
        when(seat.getSection()).thenReturn(section);
        return seat;
    }
}
