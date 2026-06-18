package com.seatrush.paymentservice.domain.payment.service;

import com.seatrush.paymentservice.common.exception.CustomException;
import com.seatrush.paymentservice.common.response.status.ErrorCode;
import com.seatrush.paymentservice.domain.event.model.PaymentResultEvent;
import com.seatrush.paymentservice.domain.event.publisher.PaymentResultEventPublisher;
import com.seatrush.paymentservice.domain.payment.dto.response.PaymentPreparationResponseDto;
import com.seatrush.paymentservice.domain.payment.dto.response.PaymentResponseDto;
import com.seatrush.paymentservice.domain.payment.entity.Payment;
import com.seatrush.paymentservice.domain.payment.entity.PaymentStatus;
import com.seatrush.paymentservice.domain.payment.repository.PaymentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 결제 요청 생성, 결제 준비 상태 조회, Mock 결제 완료 처리를 담당합니다.
 *
 * 결제 완료 시 결제 상태 변경과 결제 결과 이벤트 Outbox 저장을 하나의 트랜잭션으로 묶습니다.
 */
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentResultEventPublisher eventPublisher;

    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentResultEventPublisher eventPublisher
    ) {
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Ticket Service가 발행한 결제 요청 이벤트를 기반으로 결제 대기 데이터를 생성합니다.
     *
     * 동일한 paymentId 이벤트가 다시 들어오면 기존 데이터를 반환해 Kafka 재전달에 멱등하게 동작합니다.
     */
    @Transactional
    public PaymentResponseDto createFromEvent(
            String paymentId,
            Long reservationId,
            Long userId,
            BigDecimal amount
    ) {
        return paymentRepository.findById(paymentId)
                .map(payment -> validateDuplicateEvent(
                        payment,
                        reservationId,
                        userId,
                        amount
                ))
                .orElseGet(() -> createPayment(
                        paymentId,
                        reservationId,
                        userId,
                        amount
                ));
    }

    /**
     * 재수신된 결제 요청 이벤트가 기존 결제 정보와 같은 요청인지 확인합니다.
     */
    private PaymentResponseDto validateDuplicateEvent(
            Payment payment,
            Long reservationId,
            Long userId,
            BigDecimal amount
    ) {
        if (!payment.matchesRequest(reservationId, userId, amount)) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        return PaymentResponseDto.from(payment);
    }

    /**
     * reservationId 유니크 제약과 saveAndFlush로 중복 결제를 즉시 감지합니다.
     */
    private PaymentResponseDto createPayment(
            String paymentId,
            Long reservationId,
            Long userId,
            BigDecimal amount
    ) {
        if (paymentRepository.existsByReservationId(reservationId)) {
            throw new CustomException(ErrorCode.PAYMENT_ALREADY_EXISTS);
        }

        Payment payment = Payment.create(paymentId, reservationId, userId, amount);
        try {
            paymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException exception) {
            throw new CustomException(ErrorCode.PAYMENT_ALREADY_EXISTS, exception);
        }
        return PaymentResponseDto.from(payment);
    }

    /**
     * 프론트엔드가 paymentId를 받은 직후 결제 데이터 생성 여부를 확인할 수 있게 합니다.
     *
     * Kafka 소비가 아직 끝나지 않았으면 PROCESSING 응답을 반환합니다.
     */
    @Transactional(readOnly = true)
    public PaymentPreparationResponseDto getPreparationStatus(
            String paymentId,
            Long userId
    ) {
        return paymentRepository.findById(paymentId)
                .map(payment -> {
                    validateOwner(payment, userId);
                    return PaymentPreparationResponseDto.from(payment);
                })
                .orElseGet(() -> PaymentPreparationResponseDto.processing(paymentId));
    }

    /**
     * Mock 결제 결과를 반영하고 결제 결과 이벤트를 Outbox에 저장합니다.
     *
     * 같은 결과가 반복 요청되면 기존 상태를 유지해 멱등하게 처리합니다.
     * 서로 다른 완료 결과가 다시 들어오면 상태 역전을 막기 위해 예외를 발생시킵니다.
     */
    @Transactional
    public PaymentResponseDto complete(
            String paymentId,
            Long userId,
            PaymentStatus result
    ) {
        if (result == PaymentStatus.PENDING) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_NOT_FOUND));
        validateOwner(payment, userId);

        boolean changed;
        try {
            changed = payment.complete(result, LocalDateTime.now());
        } catch (IllegalStateException exception) {
            throw new CustomException(ErrorCode.PAYMENT_ALREADY_COMPLETED, exception);
        }

        if (changed) {
            eventPublisher.publish(PaymentResultEvent.from(payment));
        }
        return PaymentResponseDto.from(payment);
    }

    /**
     * 결제 소유자와 요청 사용자가 같은지 확인합니다.
     */
    private void validateOwner(Payment payment, Long userId) {
        if (!payment.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.PAYMENT_ACCESS_DENIED);
        }
    }
}
