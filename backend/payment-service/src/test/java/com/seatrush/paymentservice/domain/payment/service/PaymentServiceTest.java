package com.seatrush.paymentservice.domain.payment.service;

import com.seatrush.paymentservice.common.exception.CustomException;
import com.seatrush.paymentservice.common.response.status.ErrorCode;
import com.seatrush.paymentservice.domain.event.model.PaymentResultEvent;
import com.seatrush.paymentservice.domain.event.publisher.PaymentResultEventPublisher;
import com.seatrush.paymentservice.domain.payment.dto.response.PaymentPreparationStatus;
import com.seatrush.paymentservice.domain.payment.dto.response.PaymentResponseDto;
import com.seatrush.paymentservice.domain.payment.entity.Payment;
import com.seatrush.paymentservice.domain.payment.entity.PaymentStatus;
import com.seatrush.paymentservice.domain.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 결제 생성과 성공·실패 완료 및 중복 요청 처리를 검증합니다.
 */
class PaymentServiceTest {

    private PaymentRepository paymentRepository;
    private PaymentResultEventPublisher eventPublisher;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        eventPublisher = mock(PaymentResultEventPublisher.class);
        service = new PaymentService(paymentRepository, eventPublisher);
    }

    /**
     * 검증된 예매 정보로 대기 상태의 결제를 생성합니다.
     */
    @Test
    void createPendingPayment() {
        when(paymentRepository.saveAndFlush(any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponseDto response =
                service.createFromEvent(
                        "payment-1",
                        100L,
                        10L,
                        new BigDecimal("150000")
                );

        assertThat(response.paymentId()).isEqualTo("payment-1");
        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.amount()).isEqualByComparingTo("150000");
    }

    /**
     * 같은 예매에 이미 결제가 있으면 중복 생성을 거부합니다.
     */
    @Test
    void rejectDuplicatePaymentForReservation() {
        when(paymentRepository.existsByReservationId(100L)).thenReturn(true);

        assertThatThrownBy(() ->
                service.createFromEvent(
                        "payment-1",
                        100L,
                        10L,
                        new BigDecimal("150000")
                )
        )
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_ALREADY_EXISTS);
    }

    /**
     * 같은 paymentId 이벤트를 다시 소비하면 기존 결제를 반환합니다.
     */
    @Test
    void returnExistingPaymentForDuplicateEvent() {
        Payment payment = payment();
        when(paymentRepository.findById("payment-1"))
                .thenReturn(Optional.of(payment));

        PaymentResponseDto response = service.createFromEvent(
                "payment-1",
                100L,
                10L,
                new BigDecimal("150000")
        );

        assertThat(response.paymentId()).isEqualTo("payment-1");
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    /**
     * 같은 paymentId에 다른 예매 정보가 전달되면 데이터 충돌로 거부합니다.
     */
    @Test
    void rejectConflictingDuplicateEvent() {
        when(paymentRepository.findById("payment-1"))
                .thenReturn(Optional.of(payment()));

        assertThatThrownBy(() -> service.createFromEvent(
                "payment-1",
                200L,
                10L,
                new BigDecimal("150000")
        ))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    /**
     * 결제 요청 이벤트가 아직 소비되지 않았으면 준비 중 상태를 반환합니다.
     */
    @Test
    void returnProcessingBeforePaymentEventIsConsumed() {
        when(paymentRepository.findById("payment-1")).thenReturn(Optional.empty());

        var response = service.getPreparationStatus("payment-1", 10L);

        assertThat(response.paymentId()).isEqualTo("payment-1");
        assertThat(response.status()).isEqualTo(PaymentPreparationStatus.PROCESSING);
    }

    /**
     * 결제 데이터가 생성되면 Mock 결제를 처리할 수 있는 준비 완료 상태를 반환합니다.
     */
    @Test
    void returnReadyAfterPaymentEventIsConsumed() {
        when(paymentRepository.findById("payment-1")).thenReturn(Optional.of(payment()));

        var response = service.getPreparationStatus("payment-1", 10L);

        assertThat(response.status()).isEqualTo(PaymentPreparationStatus.READY);
    }

    /**
     * 다른 사용자의 결제 준비 상태 조회를 거부합니다.
     */
    @Test
    void rejectPreparationStatusForDifferentUser() {
        when(paymentRepository.findById("payment-1")).thenReturn(Optional.of(payment()));

        assertThatThrownBy(() -> service.getPreparationStatus("payment-1", 20L))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_ACCESS_DENIED);
    }

    /**
     * 대기 중인 결제를 성공 처리하고 결과 이벤트를 등록합니다.
     */
    @Test
    void completePaymentWithSuccess() {
        Payment payment = payment();
        when(paymentRepository.findByIdForUpdate("payment-1")).thenReturn(Optional.of(payment));

        PaymentResponseDto response =
                service.complete("payment-1", 10L, PaymentStatus.SUCCESS);

        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);
        verify(eventPublisher).publish(any(PaymentResultEvent.class));
    }

    /**
     * 대기 중인 결제를 실패 처리하고 결과 이벤트를 등록합니다.
     */
    @Test
    void completePaymentWithFailure() {
        Payment payment = payment();
        when(paymentRepository.findByIdForUpdate("payment-1")).thenReturn(Optional.of(payment));

        PaymentResponseDto response =
                service.complete("payment-1", 10L, PaymentStatus.FAILED);

        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
        verify(eventPublisher).publish(any(PaymentResultEvent.class));
    }

    /**
     * 같은 결과의 완료 요청을 반복하면 이벤트를 중복 등록하지 않습니다.
     */
    @Test
    void returnSamePaymentForIdempotentCompletion() {
        Payment payment = payment();
        when(paymentRepository.findByIdForUpdate("payment-1")).thenReturn(Optional.of(payment));

        service.complete("payment-1", 10L, PaymentStatus.SUCCESS);
        PaymentResponseDto response =
                service.complete("payment-1", 10L, PaymentStatus.SUCCESS);

        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);
        verify(eventPublisher, times(1))
                .publish(any(PaymentResultEvent.class));
    }

    /**
     * 완료된 결제에 반대 결과를 요청하면 변경을 거부합니다.
     */
    @Test
    void rejectDifferentResultAfterPaymentCompletion() {
        Payment payment = payment();
        payment.complete(PaymentStatus.SUCCESS, java.time.LocalDateTime.now());
        when(paymentRepository.findByIdForUpdate("payment-1")).thenReturn(Optional.of(payment));

        assertThatThrownBy(() ->
                service.complete("payment-1", 10L, PaymentStatus.FAILED)
        )
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_ALREADY_COMPLETED);
        verify(eventPublisher, never()).publish(any());
    }

    private Payment payment() {
        return Payment.create("payment-1", 100L, 10L, new BigDecimal("150000"));
    }
}
