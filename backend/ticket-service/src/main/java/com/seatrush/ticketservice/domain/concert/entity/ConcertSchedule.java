package com.seatrush.ticketservice.domain.concert.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

@Entity
@Table(name = "concert_schedules")
public class ConcertSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "concert_id", nullable = false)
    private Concert concert;

    @Column(name = "performance_at", nullable = false)
    private LocalDateTime performanceAt;

    @Column(name = "booking_open_at", nullable = false)
    private LocalDateTime bookingOpenAt;

    @Column(name = "booking_close_at", nullable = false)
    private LocalDateTime bookingCloseAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ScheduleStatus status;

    @Version
    @Column(nullable = false)
    private Long version;

    protected ConcertSchedule() {
    }

    private ConcertSchedule(
            Concert concert,
            LocalDateTime performanceAt,
            LocalDateTime bookingOpenAt,
            LocalDateTime bookingCloseAt,
            ScheduleStatus status
    ) {
        this.concert = concert;
        this.performanceAt = performanceAt;
        this.bookingOpenAt = bookingOpenAt;
        this.bookingCloseAt = bookingCloseAt;
        this.status = status;
    }

    public static ConcertSchedule create(
            Concert concert,
            LocalDateTime performanceAt,
            LocalDateTime bookingOpenAt,
            LocalDateTime bookingCloseAt
    ) {
        validatePeriod(performanceAt, bookingOpenAt, bookingCloseAt);
        return new ConcertSchedule(
                concert,
                performanceAt,
                bookingOpenAt,
                bookingCloseAt,
                ScheduleStatus.UPCOMING
        );
    }

    public void update(
            LocalDateTime performanceAt,
            LocalDateTime bookingOpenAt,
            LocalDateTime bookingCloseAt,
            ScheduleStatus status
    ) {
        validateNotCanceled();
        validatePeriod(performanceAt, bookingOpenAt, bookingCloseAt);

        this.performanceAt = performanceAt;
        this.bookingOpenAt = bookingOpenAt;
        this.bookingCloseAt = bookingCloseAt;
        this.status = status;
    }

    public void cancel() {
        validateNotCanceled();
        this.status = ScheduleStatus.CANCELED;
    }

    private static void validatePeriod(
            LocalDateTime performanceAt,
            LocalDateTime bookingOpenAt,
            LocalDateTime bookingCloseAt
    ) {
        if (!bookingOpenAt.isBefore(bookingCloseAt)
                || !bookingCloseAt.isBefore(performanceAt)) {
            throw new IllegalArgumentException("예매 시작, 예매 종료, 공연 시간 순서가 올바르지 않습니다.");
        }
    }

    private void validateNotCanceled() {
        if (status == ScheduleStatus.CANCELED) {
            throw new IllegalStateException("취소된 회차는 변경할 수 없습니다.");
        }
    }

    public Long getId() {
        return id;
    }

    public Concert getConcert() {
        return concert;
    }

    public LocalDateTime getPerformanceAt() {
        return performanceAt;
    }

    public LocalDateTime getBookingOpenAt() {
        return bookingOpenAt;
    }

    public LocalDateTime getBookingCloseAt() {
        return bookingCloseAt;
    }

    public ScheduleStatus getStatus() {
        return status;
    }

    public Long getVersion() {
        return version;
    }
}
