package com.seatrush.ticketservice.common.log;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

@Component
public class RequestCachingFilter extends OncePerRequestFilter {

    private static final String RESPONSE_DURATION_METRIC = "seat.rush.response.duration";

    private static final Pattern REAL_SEAT_QUERY_PATH =
            Pattern.compile("/api/schedules/[^/]+/seats");
    private static final Pattern PRACTICE_SEAT_QUERY_PATH =
            Pattern.compile("/api/practice-reservations/sessions/[^/]+/seat-layouts/[^/]+/seats");

    private final MeterRegistry meterRegistry;

    public RequestCachingFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        CustomHttpRequestWrapper requestWrapper = new CustomHttpRequestWrapper(request);
        String mode = resolveSeatQueryMode(request);
        Timer.Sample servletSample = mode == null ? null : Timer.start(meterRegistry);

        try {
            filterChain.doFilter(requestWrapper, response);
        } finally {
            if (servletSample != null) {
                servletSample.stop(responseTimer(mode, "servlet", response.getStatus()));
            }
        }
    }

    /**
     * 좌석 목록 응답만 별도로 계측합니다.
     * servlet 구간은 Controller, Interceptor, Service, Jackson 직렬화와 응답 쓰기가 완료될 때까지를 포함합니다.
     * 응답 본문은 로그에서 사용하지 않으므로 별도로 캐싱하지 않습니다.
     */
    private String resolveSeatQueryMode(HttpServletRequest request) {
        if (!"GET".equals(request.getMethod())) {
            return null;
        }

        String path = request.getRequestURI();
        if (REAL_SEAT_QUERY_PATH.matcher(path).matches()) {
            return "real";
        }
        if (PRACTICE_SEAT_QUERY_PATH.matcher(path).matches()) {
            return "practice";
        }
        return null;
    }

    private Timer responseTimer(String mode, String stage, int status) {
        return Timer.builder(RESPONSE_DURATION_METRIC)
                .tags(
                        "mode", mode,
                        "stage", stage,
                        "status", Integer.toString(status)
                )
                .publishPercentileHistogram()
                .register(meterRegistry);
    }
}
