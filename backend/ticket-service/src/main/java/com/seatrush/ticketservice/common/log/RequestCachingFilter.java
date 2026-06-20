package com.seatrush.ticketservice.common.log;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.regex.Pattern;

@Component
public class RequestCachingFilter extends OncePerRequestFilter {

    private static final String RESPONSE_DURATION_METRIC = "seat.rush.response.duration";
    private static final String RESPONSE_BYTES_METRIC = "seat.rush.response.bytes";

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
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        String mode = resolveSeatQueryMode(request);
        Timer.Sample mvcSample = mode == null ? null : Timer.start(meterRegistry);

        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } finally {
            if (mvcSample != null) {
                mvcSample.stop(responseTimer(mode, "mvc", responseWrapper.getStatus()));
                recordResponseBytes(mode, responseWrapper.getStatus(), responseWrapper.getContentSize());

                Timer.Sample copySample = Timer.start(meterRegistry);
                try {
                    responseWrapper.copyBodyToResponse();
                } finally {
                    copySample.stop(responseTimer(mode, "copy", responseWrapper.getStatus()));
                }
            } else {
                responseWrapper.copyBodyToResponse();
            }
        }
    }

    /**
     * 좌석 목록 응답만 별도로 계측합니다.
     * mvc 구간은 Controller, Service, Jackson 직렬화가 캐시 응답에 기록될 때까지를 포함하고,
     * copy 구간은 캐시된 응답 본문을 실제 Servlet 응답으로 전달하는 시간을 기록합니다.
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

    private void recordResponseBytes(String mode, int status, int contentSize) {
        DistributionSummary.builder(RESPONSE_BYTES_METRIC)
                .tags(
                        "mode", mode,
                        "status", Integer.toString(status)
                )
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(contentSize);
    }
}
