package com.seatrush.ticketservice.common.config;

import com.seatrush.ticketservice.common.kafka.KafkaTopic;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic scheduleStatusTopic() {
        return TopicBuilder.name(KafkaTopic.SCHEDULE_STATUS)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
