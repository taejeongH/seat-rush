package com.seatrush.ticketservice.domain.reservation.event.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.seatrush.ticketservice.domain.reservation.entity.PaymentResultApplyResult;
import com.seatrush.ticketservice.domain.reservation.event.model.PaymentResultEvent;
import com.seatrush.ticketservice.domain.reservation.event.model.PaymentResultStatus;
import com.seatrush.ticketservice.domain.reservation.service.PaymentResultService;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 결제 결과 DB 반영과 Kafka offset 커밋 순서를 검증합니다.
 */
class PaymentResultEventConsumerTest {

    /**
     * 결제 결과 반영이 성공한 뒤에만 Kafka offset을 커밋합니다.
     */
    @Test
    void acknowledgeAfterApplyingPaymentResult() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
        PaymentResultService paymentResultService = mock(PaymentResultService.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        PaymentResultEventConsumer consumer =
                new PaymentResultEventConsumer(objectMapper, paymentResultService);
        PaymentResultEvent event = new PaymentResultEvent(
                UUID.randomUUID(),
                "payment-1",
                100L,
                10L,
                new BigDecimal("150000"),
                PaymentResultStatus.SUCCESS,
                LocalDateTime.now()
        );
        when(paymentResultService.apply(event))
                .thenReturn(PaymentResultApplyResult.APPLIED);

        consumer.consume(objectMapper.writeValueAsString(event), acknowledgment);

        var order = inOrder(paymentResultService, acknowledgment);
        order.verify(paymentResultService).apply(event);
        order.verify(acknowledgment).acknowledge();
    }
}
