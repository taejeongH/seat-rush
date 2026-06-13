package com.seatrush.ticketservice.domain.seat.entity;

import com.seatrush.ticketservice.domain.concert.entity.ConcertSchedule;
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
@Table(name = "seat_sections")
public class SeatSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private ConcertSchedule schedule;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 30)
    private String grade;

    @Column(nullable = false, precision = 12, scale = 0)
    private BigDecimal price;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    protected SeatSection() {
    }

    public Long getId() {
        return id;
    }

    public ConcertSchedule getSchedule() {
        return schedule;
    }

    public String getName() {
        return name;
    }

    public String getGrade() {
        return grade;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }
}
