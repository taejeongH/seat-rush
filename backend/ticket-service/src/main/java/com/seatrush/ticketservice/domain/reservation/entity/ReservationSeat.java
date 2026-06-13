package com.seatrush.ticketservice.domain.reservation.entity;

import com.seatrush.ticketservice.domain.seat.entity.Seat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "reservation_seats")
public class ReservationSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Column(nullable = false, precision = 12, scale = 0)
    private BigDecimal price;

    protected ReservationSeat() {
    }

    private ReservationSeat(Reservation reservation, Seat seat, BigDecimal price) {
        this.reservation = reservation;
        this.seat = seat;
        this.price = price;
    }

    static ReservationSeat create(Reservation reservation, Seat seat, BigDecimal price) {
        return new ReservationSeat(reservation, seat, price);
    }

    public Long getId() {
        return id;
    }

    public Seat getSeat() {
        return seat;
    }

    public BigDecimal getPrice() {
        return price;
    }
}
