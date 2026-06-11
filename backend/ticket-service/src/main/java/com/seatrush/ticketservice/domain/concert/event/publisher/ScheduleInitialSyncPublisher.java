package com.seatrush.ticketservice.domain.concert.event.publisher;

import com.seatrush.ticketservice.domain.concert.entity.ConcertSchedule;
import com.seatrush.ticketservice.domain.concert.event.model.ScheduleEventType;
import com.seatrush.ticketservice.domain.concert.repository.ConcertScheduleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Ticket Service 시작 시 현재 회차 상태를 Kafka에 발행해 Queue Service와 초기 동기화합니다.
 */
@Component
@ConditionalOnProperty(
        prefix = "schedule",
        name = "initial-sync-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ScheduleInitialSyncPublisher {

    private static final Logger log = LoggerFactory.getLogger(ScheduleInitialSyncPublisher.class);

    private final ConcertScheduleRepository scheduleRepository;
    private final ScheduleEventPublisher eventPublisher;

    public ScheduleInitialSyncPublisher(
            ConcertScheduleRepository scheduleRepository,
            ScheduleEventPublisher eventPublisher
    ) {
        this.scheduleRepository = scheduleRepository;
        this.eventPublisher = eventPublisher;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void publishCurrentSchedules() {
        List<ConcertSchedule> schedules = scheduleRepository.findAll(Sort.by("id"));
        CompletableFuture<?>[] futures = schedules.stream()
                .map(schedule -> eventPublisher.publish(schedule, ScheduleEventType.SYNCHRONIZED))
                .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures)
                .whenComplete((result, exception) -> {
                    if (exception == null) {
                        log.info("Schedule initial synchronization completed - count={}", schedules.size());
                        return;
                    }

                    log.error("Schedule initial synchronization failed", exception);
                });
    }
}
