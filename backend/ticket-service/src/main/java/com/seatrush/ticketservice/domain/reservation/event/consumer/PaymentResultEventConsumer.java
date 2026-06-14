package com.seatrush.ticketservice.domain.reservation.event.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatrush.ticketservice.common.kafka.KafkaTopic;
import com.seatrush.ticketservice.domain.reservation.entity.PaymentResultApplyResult;
import com.seatrush.ticketservice.domain.reservation.event.model.PaymentResultEvent;
import com.seatrush.ticketservice.domain.reservation.service.PaymentResultService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class PaymentResultEventConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(PaymentResultEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final PaymentResultService paymentResultService;

    public PaymentResultEventConsumer(
            ObjectMapper objectMapper,
            PaymentResultService paymentResultService
    ) {
        this.objectMapper = objectMapper;
        this.paymentResultService = paymentResultService;
    }

    /**
     * 결제 결과의 DB 반영이 완료된 뒤에만 Kafka offset을 커밋합니다.
     */
    @KafkaListener(topics = KafkaTopic.PAYMENT_RESULT)
    public void consume(
            String payload,
            Acknowledgment acknowledgment
    ) throws JsonProcessingException {
        PaymentResultEvent event =
                objectMapper.readValue(payload, PaymentResultEvent.class);
        PaymentResultApplyResult result = paymentResultService.apply(event);

        acknowledgment.acknowledge();
        log.info(
                "Payment result event consumed - eventId={}, paymentId={}, reservationId={}, status={}, result={}",
                event.eventId(),
                event.paymentId(),
                event.reservationId(),
                event.status(),
                result
        );
    }
}
