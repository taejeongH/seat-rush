package com.seatrush.paymentservice.domain.event.publisher;

import com.seatrush.paymentservice.common.kafka.KafkaTopic;
import com.seatrush.paymentservice.domain.event.model.PaymentResultEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.TimeUnit;

@Component
public class PaymentResultEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentResultEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final long sendTimeoutSeconds;

    public PaymentResultEventPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${payment.kafka.send-timeout-seconds}") long sendTimeoutSeconds
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.sendTimeoutSeconds = sendTimeoutSeconds;
    }

    /**
     * 결제 트랜잭션이 커밋된 뒤에만 결제 결과 이벤트를 Kafka로 발행합니다.
     */
    public void publishAfterCommit(PaymentResultEvent event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("결제 결과 이벤트는 활성 트랜잭션 안에서 등록해야 합니다.");
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publish(event);
            }
        });
    }

    private void publish(PaymentResultEvent event) {
        try {
            kafkaTemplate.send(
                            KafkaTopic.PAYMENT_RESULT,
                            event.reservationId().toString(),
                            event
                    )
                    .get(sendTimeoutSeconds, TimeUnit.SECONDS);
            log.info(
                    "Payment result event published - eventId={}, paymentId={}, reservationId={}, status={}",
                    event.eventId(),
                    event.paymentId(),
                    event.reservationId(),
                    event.status()
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.error("Payment result event publishing interrupted - eventId={}", event.eventId(), exception);
        } catch (Exception exception) {
            log.error("Payment result event publishing failed - eventId={}", event.eventId(), exception);
        }
    }
}
