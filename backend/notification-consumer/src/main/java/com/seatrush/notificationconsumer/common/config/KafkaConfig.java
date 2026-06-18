package com.seatrush.notificationconsumer.common.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.seatrush.notificationconsumer.common.kafka.KafkaTopic;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * Notification Consumer에서 사용하는 Kafka 토픽과 소비 실패 처리 정책을 설정합니다.
 */
@EnableKafka
@Configuration
public class KafkaConfig {

    /**
     * 예매 완료 알림 이벤트 토픽입니다.
     */
    @Bean
    public NewTopic reservationConfirmedTopic() {
        return topic(KafkaTopic.RESERVATION_CONFIRMED);
    }

    /**
     * 예매 완료 알림 처리 실패 이벤트를 보관하는 DLT입니다.
     */
    @Bean
    public NewTopic reservationConfirmedDltTopic() {
        return topic(KafkaTopic.RESERVATION_CONFIRMED_DLT);
    }

    /**
     * 결제 실패 알림 이벤트 토픽입니다.
     */
    @Bean
    public NewTopic paymentFailedTopic() {
        return topic(KafkaTopic.PAYMENT_FAILED);
    }

    /**
     * 결제 실패 알림 처리 실패 이벤트를 보관하는 DLT입니다.
     */
    @Bean
    public NewTopic paymentFailedDltTopic() {
        return topic(KafkaTopic.PAYMENT_FAILED_DLT);
    }

    /**
     * Kafka 소비 실패 시 지수 백오프로 재시도하고, 반복 실패 이벤트는 DLT로 보냅니다.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(
                        record.topic() + ".DLT",
                        record.partition()
                )
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

    /**
     * 단일 파티션, 단일 replica 토픽을 생성합니다.
     */
    private NewTopic topic(String name) {
        return TopicBuilder.name(name)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
