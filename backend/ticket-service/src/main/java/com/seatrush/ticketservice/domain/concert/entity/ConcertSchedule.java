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

    public Long getId() {
        return id;
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
