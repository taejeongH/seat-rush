package com.seatrush.ticketservice.domain.seat.entity;

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

@Entity
@Table(name = "seats")
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false)
    private SeatSection section;

    @Column(name = "row_name", nullable = false, length = 20)
    private String rowName;

    @Column(name = "seat_number", nullable = false)
    private Integer seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SeatStatus status;

    protected Seat() {
    }

    /**
     * 결제가 성공한 좌석을 최종 예약 상태로 변경합니다.
     */
    public void reserve() {
        if (status != SeatStatus.AVAILABLE) {
            throw new IllegalStateException("예약 가능한 좌석만 확정할 수 있습니다.");
        }
        status = SeatStatus.RESERVED;
    }

    public Long getId() {
        return id;
    }

    public SeatSection getSection() {
        return section;
    }

    public String getRowName() {
        return rowName;
    }

    public Integer getSeatNumber() {
        return seatNumber;
    }

    public SeatStatus getStatus() {
        return status;
    }
}
