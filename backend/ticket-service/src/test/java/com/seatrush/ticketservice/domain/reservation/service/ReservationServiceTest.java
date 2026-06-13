package com.seatrush.ticketservice.domain.reservation.service;

import com.seatrush.ticketservice.common.exception.CustomException;
import com.seatrush.ticketservice.common.response.status.ErrorCode;
import com.seatrush.ticketservice.domain.auth.entity.User;
import com.seatrush.ticketservice.domain.auth.repository.UserRepository;
import com.seatrush.ticketservice.domain.concert.entity.ConcertSchedule;
import com.seatrush.ticketservice.domain.reservation.dto.response.ReservationResponseDto;
import com.seatrush.ticketservice.domain.reservation.entity.Reservation;
import com.seatrush.ticketservice.domain.reservation.entity.ReservationStatus;
import com.seatrush.ticketservice.domain.reservation.repository.ReservationRepository;
import com.seatrush.ticketservice.domain.seat.entity.Seat;
import com.seatrush.ticketservice.domain.seat.entity.SeatSection;
import com.seatrush.ticketservice.domain.seat.entity.SeatStatus;
import com.seatrush.ticketservice.domain.seat.repository.SeatHold;
import com.seatrush.ticketservice.domain.seat.repository.SeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 예매 생성·조회·취소 정책과 좌석 선점 연동을 검증합니다.
 */
class ReservationServiceTest {

    private ReservationRepository reservationRepository;
    private UserRepository userRepository;
    private SeatRepository seatRepository;
    private ReservationHoldReleaseService holdReleaseService;
    private ReservationService service;

    @BeforeEach
    void setUp() {
        reservationRepository = mock(ReservationRepository.class);
        userRepository = mock(UserRepository.class);
        seatRepository = mock(SeatRepository.class);
        holdReleaseService = mock(ReservationHoldReleaseService.class);
        service = new ReservationService(
                reservationRepository,
                userRepository,
                seatRepository,
                holdReleaseService
        );
    }

    /**
     * 유효한 hold의 좌석과 가격으로 결제 대기 예매를 생성합니다.
     */
    @Test
    void createReservationFromValidSeatHold() {
        ConcertSchedule schedule = schedule(1L);
        List<Seat> seats = List.of(
                seat(101L, schedule, new BigDecimal("150000")),
                seat(102L, schedule, new BigDecimal("110000"))
        );
        SeatHold hold = hold(List.of(101L, 102L));
        User user = mock(User.class);

        when(seatRepository.findAllByIdIn(List.of(101L, 102L))).thenReturn(seats);
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(reservationRepository.saveAndFlush(any(Reservation.class)))
                .thenAnswer(invocation -> {
                    Reservation reservation = invocation.getArgument(0);
                    ReflectionTestUtils.setField(reservation, "id", 100L);
                    return reservation;
                });
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);
        ReservationResponseDto response = service.create(hold, 10L, expiresAt);

        assertThat(response.reservationId()).isEqualTo(100L);
        assertThat(response.status()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(response.totalAmount()).isEqualByComparingTo("260000");
    }

    /**
     * 동일 holdId로 예매가 존재하면 중복 생성을 거부합니다.
     */
    @Test
    void rejectDuplicateReservationForSameHold() {
        when(reservationRepository.existsByHoldId("hold-1")).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                hold(List.of(101L)),
                10L,
                LocalDateTime.now().plusMinutes(10)
        ))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RESERVATION_ALREADY_EXISTS);
    }

    /**
     * 결제 대기 예매를 취소하고 커밋 후 hold 해제를 요청합니다.
     */
    @Test
    void cancelPendingReservationAndReleaseHold() {
        Reservation reservation = reservation(
                LocalDateTime.now().plusMinutes(10),
                ReservationStatus.PENDING_PAYMENT
        );
        when(reservationRepository.findByIdAndUserId(100L, 10L))
                .thenReturn(Optional.of(reservation));

        ReservationResponseDto response = service.cancel(100L, 10L);

        assertThat(response.status()).isEqualTo(ReservationStatus.CANCELED);
        verify(holdReleaseService).releaseAfterCommit("hold-1");
    }

    private SeatHold hold(List<Long> seatIds) {
        return new SeatHold(
                "hold-1",
                1L,
                10L,
                "jti-1",
                seatIds,
                Instant.now().plusSeconds(300)
        );
    }

    private ConcertSchedule schedule(Long id) {
        ConcertSchedule schedule = mock(ConcertSchedule.class);
        when(schedule.getId()).thenReturn(id);
        return schedule;
    }

    private Seat seat(
            Long id,
            ConcertSchedule schedule,
            BigDecimal price
    ) {
        SeatSection section = mock(SeatSection.class);
        Seat seat = mock(Seat.class);
        when(section.getSchedule()).thenReturn(schedule);
        when(section.getPrice()).thenReturn(price);
        when(section.getId()).thenReturn(id);
        when(section.getName()).thenReturn("VIP");
        when(seat.getId()).thenReturn(id);
        when(seat.getSection()).thenReturn(section);
        when(seat.getStatus()).thenReturn(SeatStatus.AVAILABLE);
        when(seat.getRowName()).thenReturn("A");
        when(seat.getSeatNumber()).thenReturn(id.intValue());
        return seat;
    }

    private Reservation reservation(
            LocalDateTime expiresAt,
            ReservationStatus status
    ) {
        ConcertSchedule schedule = schedule(1L);
        User user = mock(User.class);
        Reservation reservation = Reservation.create(
                user,
                schedule,
                "hold-1",
                List.of(seat(101L, schedule, new BigDecimal("150000"))),
                expiresAt
        );
        ReflectionTestUtils.setField(reservation, "id", 100L);
        if (status == ReservationStatus.CANCELED) {
            reservation.cancel();
        }
        return reservation;
    }
}
