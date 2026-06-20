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
 * 좌석 목록 요청이 API Gateway를 통과하는 시간을 실제 예매와 연습 모드로 나누어 기록합니다.
 */
@Component
public class SeatQueryGatewayMetricsFilter implements GlobalFilter, Ordered {

    private static final String GATEWAY_DURATION_METRIC = "seat.rush.gateway.duration";

    private static final Pattern REAL_SEAT_QUERY_PATH =
            Pattern.compile("/api/schedules/[^/]+/seats");
    private static final Pattern PRACTICE_SEAT_QUERY_PATH =
            Pattern.compile("/api/practice-reservations/sessions/[^/]+/seat-layouts/[^/]+/seats");

    private final MeterRegistry meterRegistry;

    public SeatQueryGatewayMetricsFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String mode = resolveSeatQueryMode(exchange);
        if (mode == null) {
            return chain.filter(exchange);
        }

        Timer.Sample sample = Timer.start(meterRegistry);
        return chain.filter(exchange)
                .doFinally(signalType -> sample.stop(gatewayTimer(mode, exchange)));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private String resolveSeatQueryMode(ServerWebExchange exchange) {
        if (!HttpMethod.GET.equals(exchange.getRequest().getMethod())) {
            return null;
        }

        String path = exchange.getRequest().getPath().value();
        if (REAL_SEAT_QUERY_PATH.matcher(path).matches()) {
            return "real";
        }
        if (PRACTICE_SEAT_QUERY_PATH.matcher(path).matches()) {
            return "practice";
        }
        return null;
    }

    private Timer gatewayTimer(String mode, ServerWebExchange exchange) {
        HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
        String status = statusCode == null ? "UNKNOWN" : Integer.toString(statusCode.value());

        return Timer.builder(GATEWAY_DURATION_METRIC)
                .tags(
                        "operation", "seat.query",
                        "mode", mode,
                        "status", status
                )
                .publishPercentileHistogram()
                .register(meterRegistry);
    }
}
