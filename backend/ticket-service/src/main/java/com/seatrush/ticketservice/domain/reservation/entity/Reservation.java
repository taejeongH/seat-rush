package com.seatrush.ticketservice.domain.reservation.entity;

import com.seatrush.ticketservice.domain.auth.entity.User;
import com.seatrush.ticketservice.domain.concert.entity.ConcertSchedule;
import com.seatrush.ticketservice.domain.seat.entity.Seat;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private ConcertSchedule schedule;

    @Column(name = "hold_id", nullable = false, unique = true, length = 100)
    private String holdId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReservationStatus status;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 0)
    private BigDecimal totalAmount;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReservationSeat> seats = new ArrayList<>();

    protected Reservation() {
    }

    private Reservation(
            User user,
            ConcertSchedule schedule,
            String holdId,
            BigDecimal totalAmount,
            LocalDateTime expiresAt
    ) {
        this.user = user;
        this.schedule = schedule;
        this.holdId = holdId;
        this.status = ReservationStatus.PENDING_PAYMENT;
        this.totalAmount = totalAmount;
        this.expiresAt = expiresAt;
    }

    public static Reservation create(
            User user,
            ConcertSchedule schedule,
            String holdId,
            List<Seat> seats,
            LocalDateTime expiresAt
    ) {
        BigDecimal totalAmount = seats.stream()
                .map(seat -> seat.getSection().getPrice())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Reservation reservation = new Reservation(
                user,
                schedule,
                holdId,
                totalAmount,
                expiresAt
        );
        seats.forEach(reservation::addSeat);
        return reservation;
    }

    public void cancel() {
        validatePendingPayment();
        status = ReservationStatus.CANCELED;
    }

    public void expire(LocalDateTime now) {
        validatePendingPayment();
        if (expiresAt.isAfter(now)) {
            throw new IllegalStateException("만료 시각 이전에는 예매를 만료할 수 없습니다.");
        }
        status = ReservationStatus.EXPIRED;
    }

    private void addSeat(Seat seat) {
        seats.add(ReservationSeat.create(this, seat, seat.getSection().getPrice()));
    }

    private void validatePendingPayment() {
        if (status != ReservationStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("결제 대기 상태의 예매만 변경할 수 있습니다.");
        }
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public ConcertSchedule getSchedule() {
        return schedule;
    }

    public String getHoldId() {
        return holdId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public Long getVersion() {
        return version;
    }

    public List<ReservationSeat> getSeats() {
        return Collections.unmodifiableList(seats);
    }
}
