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
     * 검증된 예매를 대상으로 결제 대기 정보를 생성합니다.
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
     * Kafka 결제 요청 이벤트의 소비 여부를 기준으로 결제 준비 상태를 조회합니다.
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
     * 결제 행을 잠근 뒤 Mock 성공·실패 결과를 한 번만 반영하고 이벤트를 등록합니다.
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
            eventPublisher.publishAfterCommit(PaymentResultEvent.from(payment));
        }
        return PaymentResponseDto.from(payment);
    }

    private void validateOwner(Payment payment, Long userId) {
        if (!payment.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.PAYMENT_ACCESS_DENIED);
        }
    }
}
