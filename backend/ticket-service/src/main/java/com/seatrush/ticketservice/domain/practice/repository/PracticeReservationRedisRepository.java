package com.seatrush.ticketservice.domain.practice.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Set;

@Repository
public class PracticeReservationRedisRepository {

    private static final String RESERVATION_KEY_FORMAT =
            "practice:%s:reservation:%d";
    private static final String PAYMENT_KEY_FORMAT =
            "practice:%s:payment:%s";
    private static final String SEQUENCE_KEY_FORMAT =
            "practice:%s:reservation-sequence";
    private static final String ALL_KEYS_PATTERN_FORMAT =
            "practice:%s:*";

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public PracticeReservationRedisRepository(
            RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Long nextReservationId(String practiceSessionId) {
        Long value = redisTemplate.opsForValue()
                .increment(SEQUENCE_KEY_FORMAT.formatted(practiceSessionId));
        return value == null ? 1L : value;
    }

    public void save(PracticeReservationState state, Duration ttl) {
        String value = write(state);
        redisTemplate.opsForValue().set(
                reservationKey(state.practiceSessionId(), state.reservationId()),
                value,
                ttl
        );
        redisTemplate.opsForValue().set(
                paymentKey(state.practiceSessionId(), state.paymentId()),
                state.reservationId().toString(),
                ttl
        );
    }

    public PracticeReservationState findByReservationId(
            String practiceSessionId,
            Long reservationId
    ) {
        String value = redisTemplate.opsForValue()
                .get(reservationKey(practiceSessionId, reservationId));
        return read(value);
    }

    public PracticeReservationState findByPaymentId(
            String practiceSessionId,
            String paymentId
    ) {
        String reservationId = redisTemplate.opsForValue()
                .get(paymentKey(practiceSessionId, paymentId));
        if (reservationId == null) {
            return null;
        }
        return findByReservationId(practiceSessionId, Long.valueOf(reservationId));
    }

    public void deleteSession(String practiceSessionId) {
        Set<String> keys = redisTemplate.keys(ALL_KEYS_PATTERN_FORMAT.formatted(practiceSessionId));
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private String reservationKey(String practiceSessionId, Long reservationId) {
        return RESERVATION_KEY_FORMAT.formatted(practiceSessionId, reservationId);
    }

    private String paymentKey(String practiceSessionId, String paymentId) {
        return PAYMENT_KEY_FORMAT.formatted(practiceSessionId, paymentId);
    }

    private String write(PracticeReservationState state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("practice reservation serialization failed.", exception);
        }
    }

    private PracticeReservationState read(String value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value, PracticeReservationState.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("practice reservation deserialization failed.", exception);
        }
    }
}
