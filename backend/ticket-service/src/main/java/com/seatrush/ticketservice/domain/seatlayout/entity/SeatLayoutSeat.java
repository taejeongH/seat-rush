package com.seatrush.ticketservice.domain.seatlayout.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "seat_layout_seats")
public class SeatLayoutSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false)
    private SeatLayoutSection section;

    @Column(nullable = false, length = 20)
    private String rowName;

    @Column(nullable = false)
    private Integer seatNumber;

    @Column(nullable = false)
    private Integer sortOrder;

    protected SeatLayoutSeat() {
    }

    public Long getId() {
        return id;
    }

    public SeatLayoutSection getSection() {
        return section;
    }

    public String getRowName() {
        return rowName;
    }

    public Integer getSeatNumber() {
        return seatNumber;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }
}
