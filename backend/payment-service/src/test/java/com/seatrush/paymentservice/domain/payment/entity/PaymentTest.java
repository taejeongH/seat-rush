package com.seatrush.paymentservice.domain.payment.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 결제 상태 전이와 완료 요청의 멱등성을 검증합니다.
 */
class PaymentTest {

    /**
     * 대기 중인 결제를 성공 상태로 완료합니다.
     */
    @Test
    void completePendingPaymentSuccessfully() {
        Payment payment = payment();

        boolean changed = payment.complete(PaymentStatus.SUCCESS, LocalDateTime.now());

        assertThat(changed).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getCompletedAt()).isNotNull();
    }

    /**
     * 이미 적용된 결과와 같은 완료 요청은 상태를 다시 변경하지 않습니다.
     */
    @Test
    void keepSameResultForIdempotentCompletion() {
        Payment payment = payment();
        payment.complete(PaymentStatus.FAILED, LocalDateTime.now());

        boolean changed = payment.complete(PaymentStatus.FAILED, LocalDateTime.now().plusSeconds(1));

        assertThat(changed).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    /**
     * 완료된 결제를 반대 결과로 변경할 수 없습니다.
     */
    @Test
    void rejectDifferentResultAfterCompletion() {
        Payment payment = payment();
        payment.complete(PaymentStatus.SUCCESS, LocalDateTime.now());

        assertThatThrownBy(() ->
                payment.complete(PaymentStatus.FAILED, LocalDateTime.now())
        ).isInstanceOf(IllegalStateException.class);
    }

    private Payment payment() {
        return Payment.create("payment-1", 100L, 10L, new BigDecimal("150000"));
    }
}
