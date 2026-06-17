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

import java.math.BigDecimal;

@Entity
@Table(name = "seat_layout_sections")
public class SeatLayoutSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "layout_id", nullable = false)
    private SeatLayout layout;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String grade;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer sortOrder;

    protected SeatLayoutSection() {
    }

    public Long getId() {
        return id;
    }

    public SeatLayout getLayout() {
        return layout;
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
