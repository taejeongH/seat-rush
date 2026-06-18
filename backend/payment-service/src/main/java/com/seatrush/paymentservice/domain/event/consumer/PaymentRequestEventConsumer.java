package com.seatrush.paymentservice.domain.event.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatrush.paymentservice.common.kafka.KafkaTopic;
import com.seatrush.paymentservice.domain.event.model.PaymentRequestEvent;
import com.seatrush.paymentservice.domain.payment.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Ticket Service가 발행한 결제 요청 이벤트를 소비합니다.
 *
 * 이벤트를 소비해 결제 대기 데이터를 생성하고, 처리가 끝난 뒤 Kafka offset을 수동 commit합니다.
 */
@Component
public class PaymentRequestEventConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(PaymentRequestEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final PaymentService paymentService;

    public PaymentRequestEventConsumer(
            ObjectMapper objectMapper,
            PaymentService paymentService
    ) {
        this.objectMapper = objectMapper;
        this.paymentService = paymentService;
    }

    /**
     * payment-request-v1 이벤트를 결제 대기 데이터로 변환합니다.
     */
    @KafkaListener(topics = KafkaTopic.PAYMENT_REQUEST)
    public void consume(
            String payload,
            Acknowledgment acknowledgment
    ) throws JsonProcessingException {
        PaymentRequestEvent event =
                objectMapper.readValue(payload, PaymentRequestEvent.class);

        paymentService.createFromEvent(
                event.paymentId(),
                event.reservationId(),
                event.userId(),
                event.amount()
        );

        acknowledgment.acknowledge();
        log.info(
                "Payment request event consumed - eventId={}, paymentId={}, reservationId={}",
                event.eventId(),
                event.paymentId(),
                event.reservationId()
        );
    }
}
