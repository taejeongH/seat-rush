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
            throw new IllegalStateException("이미 가상 사용자 경쟁이 실행 중입니다.");
        }

        String runId = UUID.randomUUID().toString();
        tracker.initialize(runId, request);
        List<VirtualUser> users = createUsers(request);
        Instant tokenRequiredUntil = request.startAt().toInstant()
                .plus(properties.queueWaitTimeout())
                .plus(properties.paymentWaitTimeout())
                .plus(properties.tokenRefreshBuffer());

        Instant preparationAt = request.startAt().toInstant()
                .minus(properties.preparationLeadTime());
        Disposable nextRun = delayUntil(preparationAt)
                .thenMany(Flux.fromIterable(users)
                        .flatMap(
                                user -> prepareUser(user, tokenRequiredUntil),
                                request.prepareConcurrency()
                        ))
                .collectList()
                .flatMap(preparedUsers -> accountStore.save().thenReturn(preparedUsers))
                .flatMapMany(preparedUsers -> waitUntil(request.startAt().toInstant())
                        .thenMany(runPreparedUsers(preparedUsers, request)))
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
                                ? "저장된 access token 재사용"
                                : "배포 서버 인증 준비 완료"
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
        long scheduleId = request.scheduleId();
        return apiClient.joinQueue(scheduleId, user.accessToken())
                .doOnNext(queue -> update(
                        user,
                        CompetitionStatus.WAITING,
                        "대기 순번 " + queue.path("position").asLong()
                ))
                .flatMap(queue -> {
                    if (user.behavior() == CompetitionBehavior.ABANDON_QUEUE) {
                        return think().then(markTerminal(
                                user,
                                CompetitionStatus.ABANDONED_QUEUE,
                                "대기열에서 이탈"
                        ));
                    }
                    return waitUntilEnterable(user, scheduleId);
                })
                .flatMap(ignored -> apiClient.enterQueue(scheduleId, user.accessToken()))
                .flatMap(entry -> {
                    String entryToken = entry.path("entryToken").asText();
                    update(user, CompetitionStatus.ENTERED, "좌석 선택 단계 입장");
                    if (user.behavior() == CompetitionBehavior.ABANDON_AFTER_ENTRY) {
                        return think().then(markTerminal(
                                user,
                                CompetitionStatus.ABANDONED_ENTRY,
                                "입장 후 이탈"
                        )).then(Mono.empty());
                    }
                    return think().then(holdSeats(user, scheduleId, entryToken));
                })
                .flatMap(context -> {
                    update(user, CompetitionStatus.HELD, "좌석 선점 완료");
                    if (user.behavior() == CompetitionBehavior.ABANDON_AFTER_HOLD) {
                        return think().then(markTerminal(
                                user,
                                CompetitionStatus.ABANDONED_HOLD,
                                "좌석 선점 후 이탈"
                        )).then(Mono.empty());
                    }
                    return think().then(apiClient.createReservation(
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
                    update(user, CompetitionStatus.RESERVED, "예매 생성 완료");
                    return think().then(apiClient.requestPayment(
                                    context.reservationId(),
                                    user.accessToken()
                            ))
                            .map(payment -> new PaymentContext(
                                    context.reservationId(),
                                    payment.path("paymentId").asText()
                            ));
                })
                .flatMap(context -> waitUntilPaymentReady(user, context.paymentId())
                        .then(apiClient.completePayment(
                                context.paymentId(),
                                user.accessToken(),
                                user.behavior() == CompetitionBehavior.PAYMENT_FAILURE
                                        ? "FAILED"
                                        : "SUCCESS"
                        ))
                        .then(waitUntilReservationCompleted(user, context.reservationId())))
                .flatMap(reservation -> {
                    String status = reservation.path("status").asText();
                    CompetitionStatus terminal =
                            "CONFIRMED".equals(status)
                                    ? CompetitionStatus.CONFIRMED
                                    : CompetitionStatus.PAYMENT_FAILED;
                    return markTerminal(user, terminal, "예매 상태 " + status);
                })
                .onErrorResume(error -> markTerminal(
                        user,
                        CompetitionStatus.FAILED,
                        error.getMessage()
                ));
    }

    private Mono<Void> waitUntilEnterable(VirtualUser user, long scheduleId) {
        return Flux.interval(properties.queuePollInterval())
                .concatMap(ignored -> apiClient.getQueuePosition(
                        scheduleId,
                        user.accessToken()
                ))
                .doOnNext(position -> update(
                        user,
                        CompetitionStatus.WAITING,
                        "대기 순번 " + position.path("position").asLong()
                ))
                .filter(position -> "ENTERABLE".equals(position.path("status").asText()))
                .next()
                .timeout(properties.queueWaitTimeout())
                .then();
    }

    private Mono<HoldContext> holdSeats(
            VirtualUser user,
            long scheduleId,
            String entryToken
    ) {
        return apiClient.getSections(scheduleId, user.accessToken(), entryToken)
                .flatMap(sections -> {
                    if (!sections.isArray() || sections.isEmpty()) {
                        return Mono.error(new IllegalStateException("좌석 구역이 없습니다."));
                    }
                    int sectionIndex = ThreadLocalRandom.current().nextInt(sections.size());
                    long sectionId = sections.get(sectionIndex).path("sectionId").asLong();
                    return apiClient.getSeats(
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
                        return Mono.error(new IllegalStateException("선점 가능한 좌석이 없습니다."));
                    }
                    Collections.shuffle(availableSeatIds);
                    int count = Math.min(
                            availableSeatIds.size(),
                            ThreadLocalRandom.current().nextInt(
                                    1,
                                    properties.maxSeatsPerUser() + 1
                            )
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
                .retryWhen(Retry.max(properties.seatRetryCount() - 1));
    }

    private Mono<Void> waitUntilPaymentReady(VirtualUser user, String paymentId) {
        return Flux.interval(Duration.ZERO, properties.paymentPollInterval())
                .concatMap(ignored -> apiClient.getPayment(paymentId, user.accessToken()))
                .filter(payment -> "READY".equals(payment.path("status").asText()))
                .next()
                .timeout(properties.paymentWaitTimeout())
                .then();
    }

    private Mono<JsonNode> waitUntilReservationCompleted(
            VirtualUser user,
            long reservationId
    ) {
        return Flux.interval(Duration.ZERO, properties.paymentPollInterval())
                .concatMap(ignored -> apiClient.getReservation(
                        reservationId,
                        user.accessToken()
                ))
                .filter(reservation -> {
                    String status = reservation.path("status").asText();
                    return !"PENDING_PAYMENT".equals(status)
                            && !"PAYMENT_PROCESSING".equals(status);
                })
                .next()
                .timeout(properties.paymentWaitTimeout());
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
        tracker.changeStatus(CompetitionStatus.READY);
        return delayUntil(startAt);
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
            throw new IllegalArgumentException("가상 사용자 행동 비율의 합은 100이어야 합니다.");
        }
    }

    private record HoldContext(String entryToken, String holdId) {
    }

    private record ReservationContext(String entryToken, long reservationId) {
    }

    private record PaymentContext(long reservationId, String paymentId) {
    }
}
