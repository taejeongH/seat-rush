package com.seatrush.paymentservice.domain.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "reservation_id", nullable = false, unique = true)
    private Long reservationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, precision = 12)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Payment() {
    }

    private Payment(
            String id,
            Long reservationId,
            Long userId,
            BigDecimal amount
    ) {
        LocalDateTime now = LocalDateTime.now();
        this.id = id;
        this.reservationId = reservationId;
        this.userId = userId;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Payment create(
            String paymentId,
            Long reservationId,
            Long userId,
            BigDecimal amount
    ) {
        return new Payment(paymentId, reservationId, userId, amount);
    }

    /**
     * 대기 중인 결제를 지정한 결과로 완료하고, 같은 결과의 재요청은 변경 없이 허용합니다.
     */
    public boolean complete(PaymentStatus result, LocalDateTime completedAt) {
        if (result == PaymentStatus.PENDING) {
            throw new IllegalArgumentException("결제 완료 결과는 PENDING일 수 없습니다.");
        }
        if (status == result) {
            return false;
        }
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException("이미 다른 결과로 완료된 결제입니다.");
        }

        status = result;
        this.completedAt = completedAt;
        updatedAt = completedAt;
        return true;
    }

    /**
     * 중복 소비된 결제 요청이 최초 요청과 같은 업무 데이터를 가졌는지 확인합니다.
     */
    public boolean matchesRequest(
            Long reservationId,
            Long userId,
            BigDecimal amount
    ) {
        return this.reservationId.equals(reservationId)
                && this.userId.equals(userId)
                && this.amount.compareTo(amount) == 0;
    }

    public String getId() {
        return id;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public Long getUserId() {
        return userId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
