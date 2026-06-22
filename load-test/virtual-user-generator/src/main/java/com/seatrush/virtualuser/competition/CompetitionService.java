package com.seatrush.virtualuser.competition;

import com.fasterxml.jackson.databind.JsonNode;
import com.seatrush.virtualuser.account.VirtualUserAccount;
import com.seatrush.virtualuser.account.VirtualUserAccountStore;
import com.seatrush.virtualuser.client.SeatRushApiClient;
import com.seatrush.virtualuser.competition.dto.CompetitionSnapshotResponseDto;
import com.seatrush.virtualuser.competition.dto.CompetitionStartRequestDto;
import com.seatrush.virtualuser.config.VirtualUserProperties;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class CompetitionService {

    private final SeatRushApiClient apiClient;
    private final VirtualUserProperties properties;
    private final CompetitionTracker tracker;
    private final VirtualUserAccountStore accountStore;
    private final AtomicReference<Disposable> activeRun = new AtomicReference<>();

    public CompetitionService(
            SeatRushApiClient apiClient,
            VirtualUserProperties properties,
            CompetitionTracker tracker,
            VirtualUserAccountStore accountStore
    ) {
        this.apiClient = apiClient;
        this.properties = properties;
        this.tracker = tracker;
        this.accountStore = accountStore;
    }

    public synchronized CompetitionSnapshotResponseDto start(
            CompetitionStartRequestDto request
    ) {
        validate(request);
        Disposable current = activeRun.get();
        if (current != null && !current.isDisposed()) {
            throw new IllegalStateException("virtual user competition is already running.");
        }

        String runId = UUID.randomUUID().toString();
        tracker.initialize(runId, request);
        List<VirtualUser> users = createUsers(request);
        Instant tokenRequiredUntil = Instant.now()
                .plus(Duration.ofMinutes(30))
                .plus(properties.queueWaitTimeout())
                .plus(properties.paymentWaitTimeout())
                .plus(properties.tokenRefreshBuffer());

        Disposable nextRun = Flux.fromIterable(users)
                        .flatMap(
                                user -> prepareUser(user, tokenRequiredUntil),
                                request.prepareConcurrency()
                        )
                .collectList()
                .flatMap(preparedUsers -> accountStore.save().thenReturn(preparedUsers))
                .flatMapMany(preparedUsers -> {
                    OffsetDateTime startAt = OffsetDateTime.now(ZoneOffset.UTC)
                            .plusSeconds(request.countdownSeconds());
                    OffsetDateTime closeAt = startAt.plusMinutes(request.practiceDurationMinutes());
                    return apiClient.createPracticeQueueSession(
                                    request.seatLayoutId(),
                                    request.practiceSessionId(),
                                    startAt,
                                    closeAt
                            )
                            .then(waitUntil(startAt.toInstant()))
                            .thenMany(runPreparedUsers(preparedUsers, request));
                })
                .doOnSubscribe(ignored -> tracker.changeStatus(CompetitionStatus.PREPARING))
                .doOnComplete(() -> tracker.changeStatus(CompetitionStatus.COMPLETED))
                .doOnError(error -> tracker.changeStatus(CompetitionStatus.FAILED))
                .subscribe(
                        ignored -> {
                        },
                        error -> activeRun.set(null),
                        () -> activeRun.set(null)
                );
        activeRun.set(nextRun);
        return tracker.snapshot();
    }

    public CompetitionSnapshotResponseDto getStatus() {
        return tracker.snapshot();
    }

    public Flux<CompetitionSnapshotResponseDto> getEvents() {
        return tracker.events();
    }

    public synchronized void stop() {
        Disposable current = activeRun.getAndSet(null);
        if (current != null) {
            current.dispose();
        }
        tracker.changeStatus(CompetitionStatus.COMPLETED);
    }

    private Mono<VirtualUser> prepareUser(
            VirtualUser user,
            Instant tokenRequiredUntil
    ) {
        VirtualUserAccount account = accountStore.get(user.number());
        boolean reuseToken = account.hasUsableToken(tokenRequiredUntil);
        Mono<Void> registration = account.registered()
                ? Mono.empty()
                : apiClient.signup(
                                user.email(),
                                user.password(),
                                "Virtual User " + user.number()
                        )
                        .doOnNext(ignored -> accountStore.markRegistered(user.number()))
                        .then();

        return registration.then(Mono.defer(() -> {
                    VirtualUserAccount preparedAccount = accountStore.get(user.number());
                    if (preparedAccount.hasUsableToken(tokenRequiredUntil)) {
                        user.authenticate(preparedAccount.accessToken());
                        return Mono.just(user);
                    }
                    return apiClient.login(user.email(), user.password())
                            .doOnNext(loginToken -> {
                                user.authenticate(loginToken.accessToken());
                                accountStore.updateToken(
                                        user.number(),
                                        loginToken.accessToken(),
                                        loginToken.expiresAt()
                                );
                            })
                            .thenReturn(user);
                }))
                .thenReturn(user)
                .doOnSuccess(ignored -> update(
                        user,
                        CompetitionStatus.READY,
                        reuseToken
                                ? "reused access token"
                                : "authenticated with gateway"
                ))
                .onErrorResume(error -> {
                    update(user, CompetitionStatus.FAILED, error.getMessage());
                    return Mono.empty();
                });
    }

    private Flux<VirtualUser> runPreparedUsers(
            List<VirtualUser> users,
            CompetitionStartRequestDto request
    ) {
        tracker.changeStatus(CompetitionStatus.WAITING);
        return Flux.fromIterable(users)
                .flatMap(user -> Mono.delay(randomDuration(request.joinJitterMillis()))
                                .then(runUser(user, request))
                                .thenReturn(user),
                        Math.min(users.size(), 1000)
                );
    }

    private Mono<Void> runUser(
            VirtualUser user,
            CompetitionStartRequestDto request
    ) {
        long seatLayoutId = request.seatLayoutId();
        return apiClient.joinPracticeQueue(
                        request.practiceSessionId(),
                        seatLayoutId,
                        user.accessToken()
                )
                .doOnNext(queue -> update(
                        user,
                        CompetitionStatus.WAITING,
                        "waiting position " + queue.path("position").asLong()
                ))
                .flatMap(queue -> {
                    if (user.behavior() == CompetitionBehavior.ABANDON_QUEUE) {
                        return think().then(markTerminal(
                                user,
                                CompetitionStatus.ABANDONED_QUEUE,
                                "abandoned while waiting"
                        )).then(Mono.empty());
                    }
                    return waitUntilEnterable(user, seatLayoutId, request.practiceSessionId())
                            .then(apiClient.enterPracticeQueue(
                                    request.practiceSessionId(),
                                    seatLayoutId,
                                    user.accessToken()
                            ));
                })
                .flatMap(entry -> {
                    String entryToken = entry.path("entryToken").asText();
                    update(user, CompetitionStatus.ENTERED, "entered seat selection");
                    if (user.behavior() == CompetitionBehavior.ABANDON_AFTER_ENTRY) {
                        return think().then(markTerminal(
                                user,
                                CompetitionStatus.ABANDONED_ENTRY,
                                "abandoned after entry"
                        )).then(Mono.empty());
                    }
                    return think().then(holdSeats(
                            user,
                            request.practiceSessionId(),
                            seatLayoutId,
                            entryToken
                    ));
                })
                .flatMap(context -> {
                    update(user, CompetitionStatus.HELD, "seat hold completed");
                    if (user.behavior() == CompetitionBehavior.ABANDON_AFTER_HOLD) {
                        return think().then(markTerminal(
                                user,
                                CompetitionStatus.ABANDONED_HOLD,
                                "abandoned after hold"
                        )).then(Mono.empty());
                    }
                    return think().then(apiClient.createPracticeReservation(
                                    user.accessToken(),
                                    context.entryToken(),
                                    context.holdId()
                            ))
                            .map(reservation -> new ReservationContext(
                                    context.entryToken(),
                                    reservation.path("reservationId").asLong()
                            ));
                })
                .flatMap(context -> {
                    update(user, CompetitionStatus.RESERVED, "reservation created");
                    return think().then(apiClient.requestPracticePayment(
                                    request.practiceSessionId(),
                                    context.reservationId(),
                                    user.accessToken()
                            ))
                            .map(payment -> new PaymentContext(
                                    context.reservationId(),
                                    payment.path("paymentId").asText()
                            ));
                })
                .flatMap(context -> waitUntilPaymentReady(
                                user,
                                request.practiceSessionId(),
                                context.paymentId()
                        )
                        .then(apiClient.completePracticePayment(
                                request.practiceSessionId(),
                                context.paymentId(),
                                user.accessToken(),
                                user.behavior() == CompetitionBehavior.PAYMENT_FAILURE
                                        ? "FAILED"
                                        : "SUCCESS"
                        ))
                        .then(waitUntilReservationCompleted(
                                user,
                                request.practiceSessionId(),
                                context.reservationId()
                        )))
                .flatMap(reservation -> {
                    String status = reservation.path("status").asText();
                    CompetitionStatus terminal =
                            "CONFIRMED".equals(status)
                                    ? CompetitionStatus.CONFIRMED
                                    : CompetitionStatus.PAYMENT_FAILED;
                    return markTerminal(user, terminal, "reservation status " + status);
                })
                .onErrorResume(error -> markTerminal(
                        user,
                        CompetitionStatus.FAILED,
                        error.getMessage()
                ));
    }

    private Mono<Void> waitUntilEnterable(
            VirtualUser user,
            long seatLayoutId,
            String practiceSessionId
    ) {
        return pollUntilEnterable(
                user,
                seatLayoutId,
                practiceSessionId,
                Instant.now().plus(properties.queueWaitTimeout()),
                Instant.now().plus(properties.queueHeartbeatInterval())
        );
    }

    private Mono<HoldContext> holdSeats(
            VirtualUser user,
            String practiceSessionId,
            long scheduleId,
            String entryToken
    ) {
        return apiClient.getPracticeSections(
                        practiceSessionId,
                        scheduleId,
                        user.accessToken(),
                        entryToken
                )
                .flatMap(sections -> {
                    if (!sections.isArray() || sections.isEmpty()) {
                        return Mono.error(new IllegalStateException("seat sections not found."));
                    }
                    int sectionIndex = ThreadLocalRandom.current().nextInt(sections.size());
                    long sectionId = sections.get(sectionIndex).path("sectionId").asLong();
                    return apiClient.getPracticeSeats(
                            practiceSessionId,
                            scheduleId,
                            sectionId,
                            user.accessToken(),
                            entryToken
                    );
                })
                .flatMap(seats -> {
                    List<Long> availableSeatIds = new ArrayList<>();
                    seats.forEach(seat -> {
                        if ("AVAILABLE".equals(seat.path("status").asText())) {
                            availableSeatIds.add(seat.path("seatId").asLong());
                        }
                    });
                    if (availableSeatIds.isEmpty()) {
                        return Mono.error(new IllegalStateException("available seats not found."));
                    }
                    Collections.shuffle(availableSeatIds);
                    int count = Math.min(
                            availableSeatIds.size(),
                            chooseSeatCount()
                    );
                    return apiClient.holdSeats(
                            scheduleId,
                            user.accessToken(),
                            entryToken,
                            availableSeatIds.subList(0, count)
                    );
                })
                .map(hold -> new HoldContext(
                        entryToken,
                        hold.path("holdId").asText()
                ))
                .retryWhen(Retry.backoff(
                        properties.seatRetryCount(),
                        properties.seatRetryDelay()
                ));
    }

    private int chooseSeatCount() {
        int maxSeats = Math.max(1, properties.maxSeatsPerUser());
        int weightedPick = ThreadLocalRandom.current().nextInt(10);
        int preferredCount = switch (weightedPick) {
            case 0 -> 1;
            case 1, 2 -> 2;
            case 3, 4, 5 -> 3;
            default -> 4;
        };
        return Math.min(maxSeats, preferredCount);
    }

    private Mono<Void> waitUntilPaymentReady(
            VirtualUser user,
            String practiceSessionId,
            String paymentId
    ) {
        return pollUntilPaymentReady(
                user,
                practiceSessionId,
                paymentId,
                Instant.now().plus(properties.paymentWaitTimeout())
        );
    }

    private Mono<JsonNode> waitUntilReservationCompleted(
            VirtualUser user,
            String practiceSessionId,
            long reservationId
    ) {
        return pollUntilReservationCompleted(
                user,
                practiceSessionId,
                reservationId,
                Instant.now().plus(properties.paymentWaitTimeout())
        );
    }

    private Mono<Void> pollUntilEnterable(
            VirtualUser user,
            long seatLayoutId,
            String practiceSessionId,
            Instant deadline,
            Instant nextHeartbeatAt
    ) {
        if (Instant.now().isAfter(deadline)) {
            return Mono.error(new IllegalStateException("queue wait timeout"));
        }

        boolean heartbeatRequired = !Instant.now().isBefore(nextHeartbeatAt);
        Instant updatedHeartbeatAt = heartbeatRequired
                ? Instant.now().plus(properties.queueHeartbeatInterval())
                : nextHeartbeatAt;
        Mono<Void> heartbeat = heartbeatRequired
                ? apiClient.heartbeatPracticeQueue(
                        practiceSessionId,
                        seatLayoutId,
                        user.accessToken()
                ).then()
                : Mono.empty();

        return heartbeat.then(apiClient.getPracticeQueuePosition(
                        practiceSessionId,
                        seatLayoutId,
                        user.accessToken()
                ))
                .flatMap(position -> {
                    update(
                            user,
                            CompetitionStatus.WAITING,
                            "waiting position " + position.path("position").asLong()
                    );
                    if ("ENTERABLE".equals(position.path("status").asText())) {
                        return Mono.empty();
                    }
                    return Mono.delay(properties.queuePollInterval())
                            .then(pollUntilEnterable(
                                    user,
                                    seatLayoutId,
                                    practiceSessionId,
                                    deadline,
                                    updatedHeartbeatAt
                            ));
                });
    }

    private Mono<Void> pollUntilPaymentReady(
            VirtualUser user,
            String practiceSessionId,
            String paymentId,
            Instant deadline
    ) {
        if (Instant.now().isAfter(deadline)) {
            return Mono.error(new IllegalStateException("payment preparation timeout"));
        }

        return apiClient.getPracticePayment(practiceSessionId, paymentId, user.accessToken())
                .flatMap(payment -> {
                    if ("READY".equals(payment.path("status").asText())) {
                        return Mono.empty();
                    }
                    return Mono.delay(properties.paymentPollInterval())
                            .then(pollUntilPaymentReady(
                                    user,
                                    practiceSessionId,
                                    paymentId,
                                    deadline
                            ));
                });
    }

    private Mono<JsonNode> pollUntilReservationCompleted(
            VirtualUser user,
            String practiceSessionId,
            long reservationId,
            Instant deadline
    ) {
        if (Instant.now().isAfter(deadline)) {
            return Mono.error(new IllegalStateException("reservation completion timeout"));
        }

        return apiClient.getPracticeReservation(
                        practiceSessionId,
                        reservationId,
                        user.accessToken()
                )
                .flatMap(reservation -> {
                    String status = reservation.path("status").asText();
                    if (!"PENDING_PAYMENT".equals(status)
                            && !"PAYMENT_PROCESSING".equals(status)) {
                        return Mono.just(reservation);
                    }
                    return Mono.delay(properties.paymentPollInterval())
                            .then(pollUntilReservationCompleted(
                                    user,
                                    practiceSessionId,
                                    reservationId,
                                    deadline
                            ));
                });
    }

    private Mono<Void> markTerminal(
            VirtualUser user,
            CompetitionStatus status,
            String detail
    ) {
        update(user, status, detail);
        return Mono.empty();
    }

    private Mono<Long> waitUntil(Instant startAt) {
        return Mono.defer(() -> {
            tracker.markReady(OffsetDateTime.ofInstant(startAt, ZoneOffset.UTC));
            return delayUntil(startAt);
        });
    }

    private Mono<Long> delayUntil(Instant targetAt) {
        Duration delay = Duration.between(Instant.now(), targetAt);
        if (delay.isNegative() || delay.isZero()) {
            return Mono.just(0L);
        }
        return Mono.delay(delay);
    }

    private Mono<Long> think() {
        long min = properties.actionDelayMin().toMillis();
        long max = properties.actionDelayMax().toMillis();
        long delay = ThreadLocalRandom.current().nextLong(min, max + 1);
        return Mono.delay(Duration.ofMillis(delay));
    }

    private Duration randomDuration(long maxMillis) {
        if (maxMillis == 0) {
            return Duration.ZERO;
        }
        return Duration.ofMillis(
                ThreadLocalRandom.current().nextLong(maxMillis + 1)
        );
    }

    private List<VirtualUser> createUsers(CompetitionStartRequestDto request) {
        return accountStore.ensureCapacity(request.virtualUsers()).stream()
                .map(account -> new VirtualUser(
                        account.number(),
                        account.email(),
                        account.password(),
                        selectBehavior(request.behaviors()),
                        account.accessToken()
                ))
                .toList();
    }

    private CompetitionBehavior selectBehavior(
            CompetitionStartRequestDto.BehaviorWeights weights
    ) {
        int value = ThreadLocalRandom.current().nextInt(100);
        if (value < weights.abandonQueue()) {
            return CompetitionBehavior.ABANDON_QUEUE;
        }
        value -= weights.abandonQueue();
        if (value < weights.abandonAfterEntry()) {
            return CompetitionBehavior.ABANDON_AFTER_ENTRY;
        }
        value -= weights.abandonAfterEntry();
        if (value < weights.abandonAfterHold()) {
            return CompetitionBehavior.ABANDON_AFTER_HOLD;
        }
        value -= weights.abandonAfterHold();
        if (value < weights.paymentFailure()) {
            return CompetitionBehavior.PAYMENT_FAILURE;
        }
        return CompetitionBehavior.PAYMENT_SUCCESS;
    }

    private void update(
            VirtualUser user,
            CompetitionStatus next,
            String detail
    ) {
        CompetitionStatus previous = user.status();
        user.changeStatus(next);
        tracker.updateUser(user.number(), previous, next, detail);
    }

    private void validate(CompetitionStartRequestDto request) {
        if (request.behaviors().total() != 100) {
            throw new IllegalArgumentException("behavior weight total must be 100.");
        }
    }

    private record HoldContext(String entryToken, String holdId) {
    }

    private record ReservationContext(String entryToken, long reservationId) {
    }

    private record PaymentContext(long reservationId, String paymentId) {
    }
}
