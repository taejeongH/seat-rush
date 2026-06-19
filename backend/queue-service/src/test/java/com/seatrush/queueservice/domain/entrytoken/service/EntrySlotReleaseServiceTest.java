package com.seatrush.queueservice.domain.entrytoken.service;

import com.seatrush.queueservice.common.exception.CustomException;
import com.seatrush.queueservice.domain.entrytoken.event.EntrySlotReleaseEvent;
import com.seatrush.queueservice.domain.entrytoken.event.EntrySlotReleaseReason;
import com.seatrush.queueservice.domain.entrytoken.repository.EntryTokenRedisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Entry slot release event validation and Redis delegation are verified here.
 */
class EntrySlotReleaseServiceTest {

    private EntryTokenRedisRepository entryTokenRedisRepository;
    private EntrySlotReleaseService service;

    @BeforeEach
    void setUp() {
        entryTokenRedisRepository = mock(EntryTokenRedisRepository.class);
        service = new EntrySlotReleaseService(
                entryTokenRedisRepository,
                new com.seatrush.queueservice.common.metrics.BusinessMetrics(
                        new io.micrometer.core.instrument.simple.SimpleMeterRegistry()
                )
        );
    }

    /**
     * Valid release events are delegated to Redis with schedule, user, entryTokenId, and practiceSessionId.
     */
    @Test
    void releaseEntrySlot() {
        EntrySlotReleaseEvent event = event("jti-1");
        when(entryTokenRedisRepository.releaseSlot(1L, 10L, "jti-1", "practice-1"))
                .thenReturn(true);

        boolean released = service.release(event);

        assertThat(released).isTrue();
        verify(entryTokenRedisRepository).releaseSlot(1L, 10L, "jti-1", "practice-1");
    }

    /**
     * Invalid release events are rejected before Redis is called.
     */
    @Test
    void rejectInvalidReleaseEvent() {
        EntrySlotReleaseEvent event = event("");

        assertThatThrownBy(() -> service.release(event))
                .isInstanceOf(CustomException.class);
    }

    private EntrySlotReleaseEvent event(String entryTokenId) {
        return new EntrySlotReleaseEvent(
                UUID.randomUUID(),
                100L,
                1L,
                10L,
                entryTokenId,
                EntrySlotReleaseReason.PAYMENT_SUCCESS,
                LocalDateTime.now(),
                "practice-1"
        );
    }
}
