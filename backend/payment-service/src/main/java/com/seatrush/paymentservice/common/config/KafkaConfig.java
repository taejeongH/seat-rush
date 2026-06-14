package com.seatrush.paymentservice.common.config;

import com.seatrush.paymentservice.common.kafka.KafkaTopic;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic paymentRequestTopic() {
        return TopicBuilder.name(KafkaTopic.PAYMENT_REQUEST)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentResultTopic() {
        return TopicBuilder.name(KafkaTopic.PAYMENT_RESULT)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentRequestDltTopic() {
        return TopicBuilder.name(KafkaTopic.PAYMENT_REQUEST_DLT)
                .partitions(1)
                .replicas(1)
                .build();
    }

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
