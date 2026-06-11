package com.seatrush.ticketservice.domain.concert.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;

/**
 * 회차 생성과 상태 변경에 필요한 도메인 규칙을 검증합니다.
 */
class ConcertScheduleTest {

    /**
     * 올바른 시간 순서로 회차를 생성하면 UPCOMING 상태를 가집니다.
     */
    @Test
    void createScheduleWithUpcomingStatus() {
        LocalDateTime bookingOpenAt = LocalDateTime.of(2026, 7, 1, 10, 0);
        LocalDateTime bookingCloseAt = LocalDateTime.of(2026, 7, 10, 18, 0);
        LocalDateTime performanceAt = LocalDateTime.of(2026, 7, 11, 19, 0);

        ConcertSchedule schedule = ConcertSchedule.create(
                mock(Concert.class),
                performanceAt,
                bookingOpenAt,
                bookingCloseAt
        );

        assertThat(schedule.getStatus()).isEqualTo(ScheduleStatus.UPCOMING);
    }

    /**
     * 예매 종료 시간이 공연 시간보다 늦으면 회차 생성을 거부합니다.
     */
    @Test
    void rejectInvalidSchedulePeriod() {
        LocalDateTime bookingOpenAt = LocalDateTime.of(2026, 7, 1, 10, 0);
        LocalDateTime performanceAt = LocalDateTime.of(2026, 7, 10, 18, 0);
        LocalDateTime bookingCloseAt = LocalDateTime.of(2026, 7, 11, 19, 0);

        assertThatIllegalArgumentException().isThrownBy(() ->
                ConcertSchedule.create(
                        mock(Concert.class),
                        performanceAt,
                        bookingOpenAt,
                        bookingCloseAt
                )
        );
    }

    /**
     * 취소된 회차는 다시 수정하거나 취소할 수 없습니다.
     */
    @Test
    void rejectChangesAfterCancellation() {
        LocalDateTime bookingOpenAt = LocalDateTime.of(2026, 7, 1, 10, 0);
        LocalDateTime bookingCloseAt = LocalDateTime.of(2026, 7, 10, 18, 0);
        LocalDateTime performanceAt = LocalDateTime.of(2026, 7, 11, 19, 0);
        ConcertSchedule schedule = ConcertSchedule.create(
                mock(Concert.class),
                performanceAt,
                bookingOpenAt,
                bookingCloseAt
        );

        schedule.cancel();

        assertThatIllegalStateException().isThrownBy(schedule::cancel);
        assertThatIllegalStateException().isThrownBy(() ->
                schedule.update(
                        performanceAt,
                        bookingOpenAt,
                        bookingCloseAt,
                        ScheduleStatus.BOOKING_CLOSED
                )
        );
    }
}
