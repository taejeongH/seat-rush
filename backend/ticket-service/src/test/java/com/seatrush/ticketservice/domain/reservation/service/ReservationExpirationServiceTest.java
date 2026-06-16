package com.seatrush.ticketservice.domain.reservation.service;

import com.seatrush.ticketservice.domain.auth.entity.User;
import com.seatrush.ticketservice.domain.concert.entity.ConcertSchedule;
import com.seatrush.ticketservice.domain.reservation.config.ReservationProperties;
import com.seatrush.ticketservice.domain.reservation.entity.Reservation;
import com.seatrush.ticketservice.domain.reservation.entity.ReservationStatus;
import com.seatrush.ticketservice.domain.reservation.event.model.EntrySlotReleaseReason;
import com.seatrush.ticketservice.domain.reservation.event.publisher.EntrySlotReleaseOutboxWriter;
import com.seatrush.ticketservice.domain.reservation.repository.ReservationRepository;
import com.seatrush.ticketservice.domain.seat.entity.Seat;
import com.seatrush.ticketservice.domain.seat.entity.SeatSection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 결제 기한이 지난 예매의 배치 만료 처리를 검증합니다.
 */
class ReservationExpirationServiceTest {

    /**
     * 조회된 결제 대기 예매를 만료하고 hold 해제를 예약합니다.
     */
    @Test
    void expirePendingReservationsInBatch() {
        ReservationRepository repository = mock(ReservationRepository.class);
        ReservationHoldReleaseService holdReleaseService =
                mock(ReservationHoldReleaseService.class);
        EntrySlotReleaseOutboxWriter entrySlotReleaseOutboxWriter =
                mock(EntrySlotReleaseOutboxWriter.class);
        ReservationExpirationService service = new ReservationExpirationService(
                repository,
                holdReleaseService,
                entrySlotReleaseOutboxWriter,
                new ReservationProperties(Duration.ofMinutes(10), 100)
        );
        LocalDateTime now = LocalDateTime.now();
        Reservation reservation = expiredReservation(now.minusSeconds(1));
        when(repository.findExpirableReservations(now, 100))
                .thenReturn(List.of(reservation));

        int expiredCount = service.expireBatch(now);

        assertThat(expiredCount).isEqualTo(1);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        verify(holdReleaseService).releaseAfterCommit("hold-1");
        verify(entrySlotReleaseOutboxWriter).append(
                reservation,
                EntrySlotReleaseReason.RESERVATION_EXPIRED,
                now
        );
    }

    private Reservation expiredReservation(LocalDateTime expiresAt) {
        User user = mock(User.class);
        ConcertSchedule schedule = mock(ConcertSchedule.class);
        SeatSection section = mock(SeatSection.class);
        Seat seat = mock(Seat.class);
        when(section.getSchedule()).thenReturn(schedule);
        when(section.getPrice()).thenReturn(new BigDecimal("150000"));
        when(seat.getSection()).thenReturn(section);
        return Reservation.create(
                user,
                schedule,
                "hold-1",
                "jti-1",
                List.of(seat),
                expiresAt
        );
    }
}
