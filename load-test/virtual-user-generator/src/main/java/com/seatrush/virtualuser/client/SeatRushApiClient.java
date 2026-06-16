package com.seatrush.virtualuser.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

@Component
public class SeatRushApiClient {

    private final WebClient webClient;

    public SeatRushApiClient(WebClient seatRushWebClient) {
        this.webClient = seatRushWebClient;
    }

    public Mono<Boolean> signup(String email, String password, String name) {
        return post("/api/auth/signup", null, null, Map.of(
                "email", email,
                "password", password,
                "name", name
        ))
                .thenReturn(true)
                .onErrorResume(SeatRushApiException.class, exception ->
                        "AUTH001".equals(exception.code())
                                ? Mono.just(false)
                                : Mono.error(exception)
                );
    }

    public Mono<LoginToken> login(String email, String password) {
        return post("/api/auth/login", null, null, Map.of(
                "email", email,
                "password", password
        )).map(result -> new LoginToken(
                result.path("accessToken").asText(),
                Instant.now().plusSeconds(result.path("expiresIn").asLong())
        ));
    }

    public Mono<JsonNode> joinQueue(long scheduleId, String accessToken) {
        return post("/api/schedules/%d/queues/join".formatted(scheduleId),
                accessToken, null, null);
    }

    public Mono<JsonNode> getQueuePosition(long scheduleId, String accessToken) {
        return get("/api/schedules/%d/queues/me".formatted(scheduleId),
                accessToken, null);
    }

    public Mono<JsonNode> enterQueue(long scheduleId, String accessToken) {
        return post("/api/schedules/%d/queues/enter".formatted(scheduleId),
                accessToken, null, null);
    }

    public Mono<JsonNode> getSections(long scheduleId, String accessToken, String entryToken) {
        return get("/api/schedules/%d/sections".formatted(scheduleId),
                accessToken, entryToken);
    }

    public Mono<JsonNode> getSeats(
            long scheduleId,
            long sectionId,
            String accessToken,
            String entryToken
    ) {
        return get("/api/schedules/%d/seats?sectionId=%d".formatted(scheduleId, sectionId),
                accessToken, entryToken);
    }

    public Mono<JsonNode> holdSeats(
            long scheduleId,
            String accessToken,
            String entryToken,
            java.util.List<Long> seatIds
    ) {
        return post("/api/schedules/%d/seats/hold".formatted(scheduleId),
                accessToken, entryToken, Map.of("seatIds", seatIds));
    }

    public Mono<JsonNode> createReservation(
            String accessToken,
            String entryToken,
            String holdId
    ) {
        return post("/api/reservations", accessToken, entryToken, Map.of("holdId", holdId));
    }

    public Mono<JsonNode> requestPayment(long reservationId, String accessToken) {
        return post("/api/reservations/%d/payments".formatted(reservationId),
                accessToken, null, null);
    }

    public Mono<JsonNode> getPayment(String paymentId, String accessToken) {
        return get("/api/payments/%s".formatted(paymentId), accessToken, null);
    }

    public Mono<JsonNode> completePayment(
            String paymentId,
            String accessToken,
            String result
    ) {
        return post("/api/payments/%s/complete".formatted(paymentId),
                accessToken, null, Map.of("result", result));
    }

    public Mono<JsonNode> getReservation(long reservationId, String accessToken) {
        return get("/api/reservations/%d".formatted(reservationId), accessToken, null);
    }

    private Mono<JsonNode> get(String path, String accessToken, String entryToken) {
        return exchange(webClient.get().uri(path), accessToken, entryToken);
    }

    private Mono<JsonNode> post(
            String path,
            String accessToken,
            String entryToken,
            Object body
    ) {
        WebClient.RequestBodySpec request = webClient.post().uri(path);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).bodyValue(body);
        }
        return exchange(request, accessToken, entryToken);
    }

    private Mono<JsonNode> exchange(
            WebClient.RequestHeadersSpec<?> request,
            String accessToken,
            String entryToken
    ) {
        WebClient.RequestHeadersSpec<?> authenticated = request;
        if (accessToken != null) {
            authenticated = authenticated.header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + accessToken
            );
        }
        if (entryToken != null) {
            authenticated = authenticated.header("X-Entry-Token", entryToken);
        }

        return authenticated.exchangeToMono(response ->
                response.bodyToMono(JsonNode.class)
                        .defaultIfEmpty(com.fasterxml.jackson.databind.node.NullNode.getInstance())
                        .flatMap(body -> {
                            if (response.statusCode().is2xxSuccessful()
                                    && body.path("isSuccess").asBoolean(false)) {
                                return Mono.just(body.path("result"));
                            }
                            return Mono.error(new SeatRushApiException(
                                    response.statusCode().value(),
                                    body.path("code").asText("UNKNOWN"),
                                    body.path("message").asText("요청을 처리하지 못했습니다.")
                            ));
                        })
        );
    }

    public record LoginToken(String accessToken, Instant expiresAt) {
    }
}
