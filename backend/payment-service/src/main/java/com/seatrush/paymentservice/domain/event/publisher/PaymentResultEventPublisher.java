package com.seatrush.paymentservice.domain.event.publisher;

import com.seatrush.paymentservice.common.kafka.KafkaTopic;
import com.seatrush.paymentservice.domain.event.model.PaymentResultEvent;
import com.seatrush.paymentservice.domain.outbox.service.OutboxEventService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 결제 결과 이벤트를 현재 DB 트랜잭션 안에서 Outbox에 저장합니다.
 *
 * 결제 상태 변경과 이벤트 저장이 하나의 트랜잭션으로 묶이기 때문에,
 * 결제는 성공했지만 Kafka 발행은 누락되는 상황을 줄일 수 있습니다.
 * 실제 Kafka 발행은 OutboxEventRelay가 별도 트랜잭션에서 처리합니다.
 */
@Component
public class PaymentResultEventPublisher {

    private static final String AGGREGATE_TYPE = "PAYMENT";
    private static final String EVENT_TYPE = "PAYMENT_RESULT";

    private final OutboxEventService outboxEventService;

    public PaymentResultEventPublisher(OutboxEventService outboxEventService) {
        this.outboxEventService = outboxEventService;
    }

    /**
     * 결제 결과 이벤트를 Outbox 테이블에 추가합니다.
     */
    public void publish(PaymentResultEvent event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("결제 결과 이벤트는 활성 트랜잭션 안에서 Outbox에 저장해야 합니다.");
        }

        outboxEventService.append(
                event.eventId(),
                AGGREGATE_TYPE,
                event.reservationId().toString(),
                EVENT_TYPE,
                KafkaTopic.PAYMENT_RESULT,
                event
        );
    }
}
