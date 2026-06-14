package com.seatrush.ticketservice.domain.reservation.entity;

import com.seatrush.ticketservice.domain.auth.entity.User;
import com.seatrush.ticketservice.domain.concert.entity.ConcertSchedule;
import com.seatrush.ticketservice.domain.seat.entity.Seat;
import com.seatrush.ticketservice.domain.seat.entity.SeatSection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 예매의 금액 계산과 상태 전이 규칙을 검증합니다.
 */
class ReservationTest {

    /**
     * 좌석 가격 합계로 결제 대기 예매를 생성합니다.
     */
    @Test
    void createPendingReservationWithTotalSeatPrice() {
        Reservation reservation = createReservation(
                LocalDateTime.now().plusMinutes(10),
                new BigDecimal("150000"),
                new BigDecimal("110000")
        );

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(reservation.getTotalAmount()).isEqualByComparingTo("260000");
        assertThat(reservation.getSeats()).hasSize(2);
    }

    /**
     * 결제 대기 예매를 취소 상태로 전환합니다.
     */
    @Test
    void cancelPendingReservation() {
        Reservation reservation = createReservation(
                LocalDateTime.now().plusMinutes(10),
                new BigDecimal("150000")
        );

        reservation.cancel();

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELED);
        assertThatThrownBy(reservation::cancel)
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * 만료 시각이 지난 결제 대기 예매만 만료 상태로 전환합니다.
     */
    @Test
    void expireReservationAfterPaymentDeadline() {
        LocalDateTime now = LocalDateTime.now();
        Reservation reservation = createReservation(
                now.minusSeconds(1),
                new BigDecimal("150000")
        );

        reservation.expire(now);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
    }

    /**
     * 결제 요청 시 처리 중 상태와 결제 식별자를 저장합니다.
     */
    @Test
    void transitionToPaymentProcessing() {
        Reservation reservation = createReservation(
                LocalDateTime.now().plusMinutes(10),
                new BigDecimal("150000")
        );

        boolean changed =
                reservation.requestPayment("payment-1", LocalDateTime.now());

        assertThat(changed).isTrue();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PAYMENT_PROCESSING);
        assertThat(reservation.getPaymentId()).isEqualTo("payment-1");
    }

    /**
     * 처리 중인 예매의 결제 재요청은 기존 식별자를 유지합니다.
     */
    @Test
    void keepExistingPaymentIdForRepeatedRequest() {
        Reservation reservation = createReservation(
                LocalDateTime.now().plusMinutes(10),
                new BigDecimal("150000")
        );
        reservation.requestPayment("payment-1", LocalDateTime.now());

        boolean changed =
                reservation.requestPayment("payment-2", LocalDateTime.now());

        assertThat(changed).isFalse();
        assertThat(reservation.getPaymentId()).isEqualTo("payment-1");
    }

    private Reservation createReservation(
            LocalDateTime expiresAt,
            BigDecimal... prices
    ) {
        User user = mock(User.class);
        ConcertSchedule schedule = mock(ConcertSchedule.class);
        List<Seat> seats = java.util.Arrays.stream(prices)
                .map(price -> seat(schedule, price))
                .toList();
        return Reservation.create(user, schedule, "hold-1", seats, expiresAt);
    }

    private Seat seat(ConcertSchedule schedule, BigDecimal price) {
        SeatSection section = mock(SeatSection.class);
        Seat seat = mock(Seat.class);
        when(section.getSchedule()).thenReturn(schedule);
        when(section.getPrice()).thenReturn(price);
        when(seat.getSection()).thenReturn(section);
        return seat;
    }
}
