package com.seatrush.paymentservice.domain.event.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.seatrush.paymentservice.domain.event.model.PaymentRequestEvent;
import com.seatrush.paymentservice.domain.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

/**
 * 결제 요청 이벤트 저장과 offset 커밋 순서를 검증합니다.
 */
class PaymentRequestEventConsumerTest {

    /**
     * 결제를 저장한 뒤에만 Kafka offset을 커밋합니다.
     */
    @Test
    void acknowledgeAfterCreatingPayment() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
        PaymentService paymentService = mock(PaymentService.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        PaymentRequestEventConsumer consumer =
                new PaymentRequestEventConsumer(objectMapper, paymentService);
        PaymentRequestEvent event = new PaymentRequestEvent(
                UUID.randomUUID(),
                "payment-1",
                100L,
                10L,
                new BigDecimal("150000"),
                LocalDateTime.now()
        );

        consumer.consume(objectMapper.writeValueAsString(event), acknowledgment);

        var order = inOrder(paymentService, acknowledgment);
        order.verify(paymentService).createFromEvent(
                "payment-1",
                100L,
                10L,
                new BigDecimal("150000")
        );
        order.verify(acknowledgment).acknowledge();
    }
}
