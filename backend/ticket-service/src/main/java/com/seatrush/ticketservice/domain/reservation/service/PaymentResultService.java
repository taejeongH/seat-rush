package com.seatrush.ticketservice.domain.reservation.service;

import com.seatrush.ticketservice.common.exception.CustomException;
import com.seatrush.ticketservice.common.response.status.ErrorCode;
import com.seatrush.ticketservice.domain.reservation.entity.PaymentResultApplyResult;
import com.seatrush.ticketservice.domain.reservation.entity.Reservation;
import com.seatrush.ticketservice.domain.reservation.event.model.EntrySlotReleaseReason;
import com.seatrush.ticketservice.domain.reservation.event.model.PaymentResultEvent;
import com.seatrush.ticketservice.domain.reservation.event.model.PaymentResultStatus;
import com.seatrush.ticketservice.domain.reservation.event.publisher.EntrySlotReleaseOutboxWriter;
import com.seatrush.ticketservice.domain.reservation.event.publisher.NotificationEventOutboxWriter;
import com.seatrush.ticketservice.domain.reservation.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 외부 결제 서비스로부터 Kafka를 통해 수신한 결제 처리 결과를 처리하는 서비스 클래스입니다.
 * 
 * 주요 설계 특징:
 * 1. 동시성 제어 및 상태 보호: {@link LockModeType#PESSIMISTIC_WRITE} 잠금을 사용하여 
 *    동일 예매 건에 대해 다수의 결제 결과 이벤트가 동시에 처리되는 상황을 방지하고 일관된 상태 전이를 보장합니다.
 * 2. 멱등성 보장: 결제 완료 또는 실패 처리가 이미 적용된 상태라면 중복 처리하지 않고 {@code ALREADY_APPLIED}를 반환합니다.
 * 3. Transactional Outbox 패턴: 결제 상태 변화와 관련된 부가 작업(사용자 알림 발송 및 대기열 슬롯 해제)을
 *    별도의 외부 트랜잭션으로 격리하지 않고, 동일 DB 트랜잭션 내에 Outbox 이벤트로 기록하여 데이터 일관성(Eventually Consistent)을 달성합니다.
 * 4. 커밋 후 Redis 선점 해제: DB 트랜잭션이 안전하게 commit된 이후에만 Redis의 임시 좌석 선점(Hold)을 해제하여
 *    DB 롤백 시 발생할 수 있는 좌석 선점 누수 또는 데이터 불일치를 방지합니다.
 */
@Service
public class PaymentResultService {

    private final ReservationRepository reservationRepository;
    private final ReservationHoldReleaseService holdReleaseService;
    private final EntrySlotReleaseOutboxWriter entrySlotReleaseOutboxWriter;
    private final NotificationEventOutboxWriter notificationEventOutboxWriter;

    public PaymentResultService(
            ReservationRepository reservationRepository,
            ReservationHoldReleaseService holdReleaseService,
            EntrySlotReleaseOutboxWriter entrySlotReleaseOutboxWriter,
            NotificationEventOutboxWriter notificationEventOutboxWriter
    ) {
        this.reservationRepository = reservationRepository;
        this.holdReleaseService = holdReleaseService;
        this.entrySlotReleaseOutboxWriter = entrySlotReleaseOutboxWriter;
        this.notificationEventOutboxWriter = notificationEventOutboxWriter;
    }

    /**
     * Kafka 결제 완료/실패 이벤트를 받아서 예매 엔티티의 상태를 변경하고 관련 이벤트를 Outbox 테이블에 기록합니다.
     * DB 트랜잭션 커밋 완료 후에 Redis에 잡혀있던 임시 좌석 선점을 최종 해제합니다.
     *
     * @param event Kafka를 통해 전달받은 결제 결과 이벤트 DTO
     * @return 결제 결과 반영 결과 (APPLIED: 신규 적용됨, ALREADY_APPLIED: 이미 적용되어 스킵됨)
     * @throws CustomException 예매 정보를 찾을 수 없거나 결제 데이터 검증이 실패한 경우, 혹은 유효하지 않은 상태 전이 시 발생
     */
    @Transactional
    public PaymentResultApplyResult apply(PaymentResultEvent event) {
        // 1. 필수 필드 검증 (Null Check)
        validateRequiredFields(event);

        // 2. 비관적 쓰기 잠금(PESSIMISTIC_WRITE)을 적용하여 예매 및 연관 사용자 엔티티 조회
        //    동일 예매 건에 대한 중복 처리나 상태 변경 경쟁 상태를 원천 차단합니다.
        Reservation reservation = reservationRepository
                .findByIdForPaymentResultUpdate(event.reservationId())
                .orElseThrow(() -> new CustomException(ErrorCode.RESERVATION_NOT_FOUND));

        // 3. 결제 식별자, 결제 요청자, 결제 금액이 실제 예매 데이터와 일치하는지 무결성 검증
        validateEvent(reservation, event);

        // 4. 결제 결과(SUCCESS/FAIL)에 따른 예매 상태 전이 수행 (멱등성 판단 포함)
        PaymentResultApplyResult result;
        try {
            result = event.status() == PaymentResultStatus.SUCCESS
                    ? reservation.confirmPayment()
                    : reservation.failPayment();
        } catch (IllegalStateException exception) {
            // 예매 상태 전이가 올바르지 않은 경우 (예: 이미 만료된 예약에 대해 결제 성공 처리가 온 경우 등)
            throw new CustomException(ErrorCode.PAYMENT_RESULT_STATE_CONFLICT, exception);
        }

        // 5. 신규로 상태가 반영된 경우에만 Transactional Outbox 메시지 기록
        if (result == PaymentResultApplyResult.APPLIED) {
            LocalDateTime occurredAt = LocalDateTime.now();
            
            // 알림 메시지 발송을 위한 Outbox 등록
            appendNotificationEvent(reservation, event.status(), occurredAt);
            
            // 대기열 서비스에 해당 사용자의 슬롯이 반환되도록 대기열 해제용 Outbox 등록
            entrySlotReleaseOutboxWriter.append(
                    reservation,
                    releaseReason(event.status()),
                    occurredAt
            );
        }

        // 6. DB 커밋 완료 이후 Redis의 좌석 선점을 안전하게 해제하도록 리스너 등록
        holdReleaseService.releaseAfterCommit(reservation.getHoldId());
        
        return result;
    }

    /**
     * 결제 결과 상태에 따라 알림 종류를 분기하여 Outbox 테이블에 저장합니다.
     */
    private void appendNotificationEvent(
            Reservation reservation,
            PaymentResultStatus status,
            LocalDateTime occurredAt
    ) {
        if (status == PaymentResultStatus.SUCCESS) {
            notificationEventOutboxWriter.appendReservationConfirmed(
                    reservation,
                    occurredAt
            );
            return;
        }
        notificationEventOutboxWriter.appendPaymentFailed(
                reservation,
                occurredAt
        );
    }

    /**
     * 결제 결과 상태에 따른 대기열 반환 사유를 매핑합니다.
     */
    private EntrySlotReleaseReason releaseReason(PaymentResultStatus status) {
        if (status == PaymentResultStatus.SUCCESS) {
            return EntrySlotReleaseReason.PAYMENT_SUCCESS;
        }
        return EntrySlotReleaseReason.PAYMENT_FAILED;
    }

    /**
     * Kafka 결제 이벤트의 필수 값 누락 여부를 검증합니다.
     */
    private void validateRequiredFields(PaymentResultEvent event) {
        if (event == null
                || event.eventId() == null
                || event.paymentId() == null
                || event.reservationId() == null
                || event.userId() == null
                || event.amount() == null
                || event.status() == null
                || event.occurredAt() == null) {
            throw new CustomException(ErrorCode.PAYMENT_RESULT_MISMATCH);
        }
    }

    /**
     * 수신한 결제 정보(결제 ID, 사용자 ID, 총 금액)가 DB 내 예매 정보와 완벽히 일치하는지 대조합니다.
     */
    private void validateEvent(
            Reservation reservation,
            PaymentResultEvent event
    ) {
        boolean mismatch = !event.paymentId().equals(reservation.getPaymentId())
                || !event.userId().equals(reservation.getUser().getId())
                || event.amount().compareTo(reservation.getTotalAmount()) != 0;
        if (mismatch) {
            throw new CustomException(ErrorCode.PAYMENT_RESULT_MISMATCH);
        }
    }
}
