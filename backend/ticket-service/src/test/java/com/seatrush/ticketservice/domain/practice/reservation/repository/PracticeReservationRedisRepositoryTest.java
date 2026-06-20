package com.seatrush.ticketservice.domain.practice.reservation.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 연습 세션 Redis 데이터 정리 방식을 검증합니다.
 */
class PracticeReservationRedisRepositoryTest {

    /**
     * 세션 정리는 KEYS 대신 SCAN으로 조회한 키만 삭제합니다.
     */
    @Test
    @SuppressWarnings("unchecked")
    void deleteSessionScansAndDeletesOnlySessionKeys() {
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        Cursor<String> cursor = mock(Cursor.class);
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(true, true, false);
        when(cursor.next()).thenReturn(
                "practice:session-1:reservation:1",
                "practice:session-1:payment:payment-1"
        );

        PracticeReservationRedisRepository repository = new PracticeReservationRedisRepository(
                redisTemplate,
                new ObjectMapper()
        );

        repository.deleteSession("session-1");

        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).delete(keysCaptor.capture());
        verify(redisTemplate, never()).keys(anyString());
        assertThat(keysCaptor.getValue()).containsExactly(
                "practice:session-1:reservation:1",
                "practice:session-1:payment:payment-1"
        );
    }
}
