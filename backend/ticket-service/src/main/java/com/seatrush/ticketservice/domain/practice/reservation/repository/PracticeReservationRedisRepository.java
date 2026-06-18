package com.seatrush.ticketservice.domain.practice.reservation.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Set;

/**
 * 연습 모드(Practice Mode) 전용 데이터(가상 예매 정보, 가상 결제 상태, 시퀀스 등)를
 * Redis 캐시 저장소에 격리하여 저장 및 관리하는 레포지토리 클래스입니다.
 * 
 * 모든 키는 `practice:{sessionId}:...` 패턴의 Prefix를 갖도록 구성하여,
 * 연습 세션 종료 시 해당 세션에 종속된 키들을 한꺼번에 쉽게 정리할 수 있게 설계되었습니다.
 */
@Repository
public class PracticeReservationRedisRepository {

    // 예매 상세 상태 키 포맷: practice:{sessionId}:reservation:{reservationId} -> JSON Value
    private static final String RESERVATION_KEY_FORMAT =
            "practice:%s:reservation:%d";
            
    // 결제 매칭용 임시 키 포맷: practice:{sessionId}:payment:{paymentId} -> reservationId (String)
    private static final String PAYMENT_KEY_FORMAT =
            "practice:%s:payment:%s";
            
    // 예매 auto-increment 모사용 Redis 시퀀스 키 포맷: practice:{sessionId}:reservation-sequence -> Long
    private static final String SEQUENCE_KEY_FORMAT =
            "practice:%s:reservation-sequence";
            
    // 특정 세션의 모든 키 조회를 위한 와일드카드 패턴 포맷: practice:{sessionId}:*
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

    /**
     * Redis의 `INCR` 명령을 활용하여 분산 환경에서도 겹치지 않는 모조 가상 예매 ID를 1씩 증가시키며 가져옵니다.
     *
     * @param practiceSessionId 고유 연습 세션 식별 ID
     * @return 발급받은 다음 가상 예매 ID
     */
    public Long nextReservationId(String practiceSessionId) {
        Long value = redisTemplate.opsForValue()
                .increment(SEQUENCE_KEY_FORMAT.formatted(practiceSessionId));
        return value == null ? 1L : value;
    }

    /**
     * 가상 예매 상태 정보를 Redis에 저장하고, 결제 ID 매핑 정보도 함께 기록합니다.
     * 지정한 보존 기한(TTL)을 만료시간으로 설정하여 좀비 데이터가 쌓이는 것을 방지합니다.
     *
     * @param state 저장할 가상 예매 상태 Dto
     * @param ttl 데이터 보존 기한
     */
    public void save(PracticeReservationState state, Duration ttl) {
        String value = write(state);
        
        // 예매 상세 객체 저장 (Key: practice:{sessionId}:reservation:{reservationId})
        redisTemplate.opsForValue().set(
                reservationKey(state.practiceSessionId(), state.reservationId()),
                value,
                ttl
        );
        
        // 결제 ID 매칭 인덱스용 키 저장 (Key: practice:{sessionId}:payment:{paymentId} -> Value: reservationId)
        redisTemplate.opsForValue().set(
                paymentKey(state.practiceSessionId(), state.paymentId()),
                state.reservationId().toString(),
                ttl
        );
    }

    /**
     * 연습 세션 ID와 예매 ID를 기준으로 가상 예매 상세 정보를 복원하여 조회합니다.
     */
    public PracticeReservationState findByReservationId(
            String practiceSessionId,
            Long reservationId
    ) {
        String value = redisTemplate.opsForValue()
                .get(reservationKey(practiceSessionId, reservationId));
        return read(value);
    }

    /**
     * 결제 ID(paymentId)를 기준으로 매칭되는 가상 예매 상세 정보를 탐색하여 복원합니다.
     */
    public PracticeReservationState findByPaymentId(
            String practiceSessionId,
            String paymentId
    ) {
        // 결제 키를 통해 예매 ID를 먼저 획득
        String reservationId = redisTemplate.opsForValue()
                .get(paymentKey(practiceSessionId, paymentId));
        if (reservationId == null) {
            return null;
        }
        return findByReservationId(practiceSessionId, Long.valueOf(reservationId));
    }

    /**
     * 연습 세션 종료 또는 리셋 시, 해당 세션에 종속되어 생성된 모든 키들을 Redis 패턴 조회를 통해 일괄 삭제합니다.
     *
     * @param practiceSessionId 삭제할 대상 고유 연습 세션 ID
     */
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

    /**
     * 객체를 Redis에 텍스트 형태로 저장하기 위해 JSON 문자열로 직렬화합니다.
     */
    private String write(PracticeReservationState state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("practice reservation serialization failed.", exception);
        }
    }

    /**
     * Redis에서 가져온 JSON 문자열 데이터를 실제 객체 인스턴스로 역직렬화합니다.
     */
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
