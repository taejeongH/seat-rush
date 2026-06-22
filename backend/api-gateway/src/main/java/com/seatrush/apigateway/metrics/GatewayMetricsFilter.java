package com.seatrush.apigateway.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.regex.Pattern;

/**
 * 성능 측정 대상 API가 Gateway를 통과하는 시간을 실제·연습 모드별로 기록합니다.
 */
@Component
public class GatewayMetricsFilter implements GlobalFilter, Ordered {

    private static final String GATEWAY_DURATION_METRIC = "seat.rush.gateway.duration";

    private static final Pattern REAL_SEAT_QUERY_PATH =
            Pattern.compile("/api/schedules/[^/]+/seats");
    private static final Pattern PRACTICE_SEAT_QUERY_PATH =
            Pattern.compile("/api/practice-reservations/sessions/[^/]+/seat-layouts/[^/]+/seats");
    private static final Pattern REAL_QUEUE_ENTER_PATH =
            Pattern.compile("/api/schedules/[^/]+/queues/enter");
    private static final Pattern PRACTICE_QUEUE_ENTER_PATH =
            Pattern.compile("/api/practice/sessions/[^/]+/seat-layouts/[^/]+/queues/enter");

    private final MeterRegistry meterRegistry;

    public GatewayMetricsFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        GatewayMetricContext context = resolveMetricContext(exchange);
        if (context == null) {
            return chain.filter(exchange);
        }

        Timer.Sample sample = Timer.start(meterRegistry);
        return chain.filter(exchange)
                .doFinally(signalType -> sample.stop(gatewayTimer(context, exchange)));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private GatewayMetricContext resolveMetricContext(ServerWebExchange exchange) {
        HttpMethod method = exchange.getRequest().getMethod();
        String path = exchange.getRequest().getPath().value();

        if (HttpMethod.GET.equals(method)) {
            if (REAL_SEAT_QUERY_PATH.matcher(path).matches()) {
                return new GatewayMetricContext("seat.query", "real");
            }
            if (PRACTICE_SEAT_QUERY_PATH.matcher(path).matches()) {
                return new GatewayMetricContext("seat.query", "practice");
            }
        }

        if (HttpMethod.POST.equals(method)) {
            if (REAL_QUEUE_ENTER_PATH.matcher(path).matches()) {
                return new GatewayMetricContext("queue.enter", "real");
            }
            if (PRACTICE_QUEUE_ENTER_PATH.matcher(path).matches()) {
                return new GatewayMetricContext("queue.enter", "practice");
            }
        }
        return null;
    }

    private Timer gatewayTimer(GatewayMetricContext context, ServerWebExchange exchange) {
        HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
        String status = statusCode == null ? "UNKNOWN" : Integer.toString(statusCode.value());

        return Timer.builder(GATEWAY_DURATION_METRIC)
                .tags(
                        "operation", context.operation(),
                        "mode", context.mode(),
                        "status", status
                )
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    private record GatewayMetricContext(String operation, String mode) {
    }
}
