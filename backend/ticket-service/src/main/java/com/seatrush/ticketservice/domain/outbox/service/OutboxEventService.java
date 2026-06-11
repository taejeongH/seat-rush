package com.seatrush.ticketservice.domain.outbox.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatrush.ticketservice.domain.outbox.entity.OutboxEvent;
import com.seatrush.ticketservice.domain.outbox.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 도메인 이벤트를 JSON으로 직렬화해 Outbox에 저장합니다.
 */
@Service
public class OutboxEventService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventService(
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    public void append(
            UUID eventId,
            String aggregateType,
            Long aggregateId,
            String eventType,
            String topic,
            Object payload
    ) {
        try {
            outboxEventRepository.save(OutboxEvent.create(
                    eventId,
                    aggregateType,
                    aggregateId,
                    eventType,
                    topic,
                    objectMapper.writeValueAsString(payload)
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Outbox 이벤트 직렬화에 실패했습니다.", exception);
        }
    }
}
