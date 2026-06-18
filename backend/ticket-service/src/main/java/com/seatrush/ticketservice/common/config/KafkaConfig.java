package com.seatrush.ticketservice.common.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.seatrush.ticketservice.common.kafka.KafkaTopic;
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
 * 카프카 브로커와의 통신에 필요한 토픽들을 생성하고,
 * 컨슈머 비즈니스 처리 도중 실패 시 예외 재시도 및 DLT(Dead Letter Topic)로 복구하기 위한 에러 핸들러 설정 클래스입니다.
 */
@Configuration
public class KafkaConfig {

    /**
     * 콘서트 스케줄의 상태(예: 예매 오픈 등) 이벤트를 전송할 토픽 생성 빈입니다.
     */
    @Bean
    public NewTopic scheduleStatusTopic() {
        return TopicBuilder.name(KafkaTopic.SCHEDULE_STATUS)
                .partitions(1)
                .replicas(1)
                .build();
    }

    /**
     * 결제 서비스로 결제 요청 메시지를 발송하는 토픽 생성 빈입니다.
     */
    @Bean
    public NewTopic paymentRequestTopic() {
        return TopicBuilder.name(KafkaTopic.PAYMENT_REQUEST)
                .partitions(1)
                .replicas(1)
                .build();
    }

    /**
     * 결제 서비스로부터 결제 완료 결과를 수신하는 토픽 생성 빈입니다.
     */
    @Bean
    public NewTopic paymentResultTopic() {
        return TopicBuilder.name(KafkaTopic.PAYMENT_RESULT)
                .partitions(1)
                .replicas(1)
                .build();
    }

    /**
     * 결제 결과 수신 중 복구 불가능한 에러가 발생한 메시지를 적재하는 DLT 토픽 생성 빈입니다.
     */
    @Bean
    public NewTopic paymentResultDltTopic() {
        return TopicBuilder.name(KafkaTopic.PAYMENT_RESULT_DLT)
                .partitions(1)
                .replicas(1)
                .build();
    }

    /**
     * 최종 예약 확정 완료 알림(이메일 발송 등)을 연동하기 위한 토픽 생성 빈입니다.
     */
    @Bean
    public NewTopic reservationConfirmedTopic() {
        return TopicBuilder.name(KafkaTopic.RESERVATION_CONFIRMED)
                .partitions(1)
                .replicas(1)
                .build();
    }

    /**
     * 결제 실패 상태 이벤트를 알리는 토픽 생성 빈입니다.
     */
    @Bean
    public NewTopic paymentFailedTopic() {
        return TopicBuilder.name(KafkaTopic.PAYMENT_FAILED)
                .partitions(1)
                .replicas(1)
                .build();
    }

    /**
     * 대기열 세션 만료 및 예약 만료 등에 의해 진입 슬롯을 반환하기 위해 발행하는 토픽 생성 빈입니다.
     */
    @Bean
    public NewTopic entrySlotReleaseTopic() {
        return TopicBuilder.name(KafkaTopic.ENTRY_SLOT_RELEASE)
                .partitions(1)
                .replicas(1)
                .build();
    }

    /**
     * 카프카 메시지 소비(Consumer) 과정에서 발생하는 예외를 일관되게 처리하는 글로벌 에러 핸들러를 정의합니다.
     * 
     * - 예외 발생 시 최대 3회 재시도(Retry)하며, 재시도 간격은 1초에서 최대 10초까지 지수적으로 증가(Exponential Backoff)합니다.
     * - 최대 재시도 횟수를 초과한 메시지는 DLT(Dead Letter Topic)인 `payment-result-v1.DLT`로 강제 전송(Recover)하여 데이터 유실을 방지합니다.
     * - 역직렬화 오류 등 데이터 자체 규격 에러({@link JsonProcessingException})는 재시도 없이 즉시 DLT로 이동시킵니다.
     *
     * @param kafkaTemplate DLT 전송에 사용할 KafkaTemplate
     * @return 카프카 에러 핸들러 Bean
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, Object> kafkaTemplate
    ) {
        // 메시지 처리 실패 시 DLT 토픽 파티션으로 레코드를 전송하는 에러 복구 핸들러(Recoverer) 설정
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) ->
                        new TopicPartition(KafkaTopic.PAYMENT_RESULT_DLT, record.partition())
        );

        // 지수적 백오프 정책 (시작 간격 1초, 곱수 2.0배, 최대 10초, 최대 3회 시도)
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(1_000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(10_000L);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        
        // JSON 파싱 에러처럼 데이터 자체의 결함으로 인한 포맷 에러는 반복 시도 무의미하므로 즉시 DLT로 전송(Recover)
        errorHandler.addNotRetryableExceptions(JsonProcessingException.class);
        errorHandler.setCommitRecovered(true); // 복구 완료된 메시지는 커밋 처리하여 다음 오프셋 진행
        return errorHandler;
    }
}

