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

@EnableKafka
@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic reservationConfirmedTopic() {
        return topic(KafkaTopic.RESERVATION_CONFIRMED);
    }

    @Bean
    public NewTopic reservationConfirmedDltTopic() {
        return topic(KafkaTopic.RESERVATION_CONFIRMED_DLT);
    }

    @Bean
    public NewTopic paymentFailedTopic() {
        return topic(KafkaTopic.PAYMENT_FAILED);
    }

    @Bean
    public NewTopic paymentFailedDltTopic() {
        return topic(KafkaTopic.PAYMENT_FAILED_DLT);
    }

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

    private NewTopic topic(String name) {
        return TopicBuilder.name(name)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
