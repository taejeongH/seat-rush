package com.seatrush.ticketservice.domain.practice.reservation.event;

import com.seatrush.ticketservice.common.kafka.KafkaTopic;
import com.seatrush.ticketservice.domain.practice.reservation.repository.PracticeReservationState;
import com.seatrush.ticketservice.domain.reservation.event.model.EntrySlotReleaseEvent;
import com.seatrush.ticketservice.domain.reservation.event.model.EntrySlotReleaseReason;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 연습 모드 결제 완료/실패 시, 대기열 서비스(Queue Service)에 진입 차단 슬롯을 반환하도록
 * Kafka를 통해 대기열 슬롯 해제 이벤트를 발행하는 이벤트 퍼블리셔 컴포넌트입니다.
 */
@Component
public class PracticeEntrySlotReleasePublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PracticeEntrySlotReleasePublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * 가상 예매의 상태와 해제 사유를 담은 {@link EntrySlotReleaseEvent}를 생성하여 Kafka로 비동기 송신합니다.
     *
     * @param state 가상 예매 세션 상태 정보 객체
     * @param reason 대기열 해제 사유 (예: PAYMENT_SUCCESS, PAYMENT_FAILED 등)
     */
    public void publish(
            PracticeReservationState state,
            EntrySlotReleaseReason reason
    ) {
        EntrySlotReleaseEvent event = new EntrySlotReleaseEvent(
                UUID.randomUUID(),
                state.reservationId(),
                state.scheduleId(),
                state.userId(),
                state.entryTokenId(),
                reason,
                LocalDateTime.now(),
                state.practiceSessionId()
        );
        kafkaTemplate.send(
                KafkaTopic.ENTRY_SLOT_RELEASE,
                state.scheduleId().toString(),
                event
        );
    }
}
