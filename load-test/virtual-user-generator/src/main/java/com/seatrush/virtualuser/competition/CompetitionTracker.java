package com.seatrush.virtualuser.competition;

import com.seatrush.virtualuser.competition.dto.CompetitionEventResponseDto;
import com.seatrush.virtualuser.competition.dto.CompetitionSnapshotResponseDto;
import com.seatrush.virtualuser.competition.dto.CompetitionStartRequestDto;
import com.seatrush.virtualuser.config.VirtualUserProperties;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class CompetitionTracker {

    private static final int MAX_RECENT_EVENTS = 100;

    private final VirtualUserProperties properties;
    private final Sinks.Many<CompetitionSnapshotResponseDto> sink =
            Sinks.many().replay().latest();
    private final Map<CompetitionStatus, Long> userStatuses =
            new EnumMap<>(CompetitionStatus.class);
    private final ArrayDeque<CompetitionEventResponseDto> recentEvents =
            new ArrayDeque<>();

    private String runId;
    private CompetitionStatus status = CompetitionStatus.IDLE;
    private CompetitionStartRequestDto request;
    private int completedUsers;

    public CompetitionTracker(VirtualUserProperties properties) {
        this.properties = properties;
    }

    public synchronized void initialize(
            String nextRunId,
            CompetitionStartRequestDto nextRequest
    ) {
        runId = nextRunId;
        request = nextRequest;
        status = CompetitionStatus.PREPARING;
        completedUsers = 0;
        userStatuses.clear();
        recentEvents.clear();
        userStatuses.put(CompetitionStatus.PREPARING, (long) nextRequest.virtualUsers());
        emit();
    }

    public synchronized void updateUser(
            int userNumber,
            CompetitionStatus previous,
            CompetitionStatus next,
            String detail
    ) {
        if (previous != null) {
            userStatuses.computeIfPresent(previous, (key, count) -> Math.max(0, count - 1));
        }
        userStatuses.merge(next, 1L, Long::sum);
        if (isTerminal(next)) {
            completedUsers++;
        }

        recentEvents.addFirst(new CompetitionEventResponseDto(
                userNumber,
                next.name(),
                detail,
                Instant.now()
        ));
        while (recentEvents.size() > MAX_RECENT_EVENTS) {
            recentEvents.removeLast();
        }
        emit();
    }

    public synchronized void changeStatus(CompetitionStatus next) {
        status = next;
        emit();
    }

    public synchronized CompetitionSnapshotResponseDto snapshot() {
        if (request == null) {
            return new CompetitionSnapshotResponseDto(
                    null,
                    CompetitionStatus.IDLE,
                    properties.gatewayBaseUrl(),
                    null,
                    0,
                    0,
                    null,
                    Instant.now(),
                    Map.of(),
                    List.of()
            );
        }

        Map<String, Long> statuses = userStatuses.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        entry -> entry.getKey().name(),
                        Map.Entry::getValue
                ));
        return new CompetitionSnapshotResponseDto(
                runId,
                status,
                properties.gatewayBaseUrl(),
                request.scheduleId(),
                request.virtualUsers(),
                completedUsers,
                request.startAt(),
                Instant.now(),
                statuses,
                List.copyOf(recentEvents)
        );
    }

    public Flux<CompetitionSnapshotResponseDto> events() {
        return sink.asFlux();
    }

    private void emit() {
        sink.tryEmitNext(snapshot());
    }

    private boolean isTerminal(CompetitionStatus value) {
        return switch (value) {
            case ABANDONED_QUEUE, ABANDONED_ENTRY, ABANDONED_HOLD,
                    PAYMENT_FAILED, CONFIRMED, FAILED -> true;
            default -> false;
        };
    }
}
