package com.seatrush.queueservice.common.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.seatrush.queueservice.common.kafka.KafkaTopic;
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
 * Queue Service에서 사용하는 Kafka 토픽과 소비 실패 처리 정책을 설정합니다.
 *
 * 회차 상태 동기화와 입장 슬롯 반환 이벤트는 실패 시 재시도 후 DLT로 이동합니다.
 */
@Configuration
public class KafkaConfig {

    /**
     * 회차 상태 동기화 실패 이벤트를 보관하는 DLT입니다.
     */
    @Bean
    public NewTopic scheduleStatusDltTopic() {
        return TopicBuilder.name(KafkaTopic.SCHEDULE_STATUS_DLT)
                .partitions(1)
                .replicas(1)
                .build();
    }

    /**
     * Ticket Service가 입장 슬롯 반환을 요청하는 토픽입니다.
     */
    @Bean
    public NewTopic entrySlotReleaseTopic() {
        return TopicBuilder.name(KafkaTopic.ENTRY_SLOT_RELEASE)
                .partitions(1)
                .replicas(1)
                .build();
    }

    /**
     * 입장 슬롯 반환 실패 이벤트를 보관하는 DLT입니다.
     */
    @Bean
    public NewTopic entrySlotReleaseDltTopic() {
        return TopicBuilder.name(KafkaTopic.ENTRY_SLOT_RELEASE_DLT)
                .partitions(1)
                .replicas(1)
                .build();
    }

    /**
     * Kafka 소비 실패 시 지수 백오프로 재시도하고, 반복 실패 이벤트는 DLT로 보냅니다.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
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
}
