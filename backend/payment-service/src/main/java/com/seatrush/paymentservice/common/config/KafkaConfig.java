package com.seatrush.paymentservice.common.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.seatrush.paymentservice.common.kafka.KafkaTopic;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * Payment Service에서 사용하는 Kafka 토픽과 소비 실패 처리 정책을 설정합니다.
 */
@Configuration
public class KafkaConfig {

    /**
     * Ticket Service가 결제 요청을 전달하는 토픽입니다.
     */
    @Bean
    public NewTopic paymentRequestTopic() {
        return TopicBuilder.name(KafkaTopic.PAYMENT_REQUEST)
                .partitions(1)
                .replicas(1)
                .build();
    }

    /**
     * Payment Service가 결제 결과를 전달하는 토픽입니다.
     */
    @Bean
    public NewTopic paymentResultTopic() {
        return TopicBuilder.name(KafkaTopic.PAYMENT_RESULT)
                .partitions(1)
                .replicas(1)
                .build();
    }

    /**
     * 결제 요청 이벤트 소비 실패 메시지를 보관하는 DLT입니다.
     */
    @Bean
    public NewTopic paymentRequestDltTopic() {
        return TopicBuilder.name(KafkaTopic.PAYMENT_REQUEST_DLT)
                .partitions(1)
                .replicas(1)
                .build();
    }

    /**
     * Kafka 소비 실패 시 지수 백오프로 재시도하고, 반복 실패 이벤트는 DLT로 보냅니다.
     *
     * JSON 역직렬화 실패는 재시도해도 성공 가능성이 낮기 때문에 바로 DLT 대상으로 분류합니다.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, Object> kafkaTemplate
    ) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) ->
                        new TopicPartition(KafkaTopic.PAYMENT_REQUEST_DLT, record.partition())
        );

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(1_000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(10_000L);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(JsonProcessingException.class);
        errorHandler.setCommitRecovered(true);
        return errorHandler;
    }
}
