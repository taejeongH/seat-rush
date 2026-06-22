package com.seatrush.ticketservice.common.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.function.Function;
import java.util.function.Supplier;

@Component
public class BusinessMetrics {

    private static final String DURATION_METRIC = "seat.rush.business.duration";
    private static final String EVENT_METRIC = "seat.rush.business.events";

    private final MeterRegistry meterRegistry;

    public BusinessMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public <T> T record(String operation, String mode, Supplier<T> supplier) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            T result = supplier.get();
            recordEvent(operation, mode, "success");
            sample.stop(timer(operation, mode, "success"));
            return result;
        } catch (RuntimeException exception) {
            recordEvent(operation, mode, "failure");
            sample.stop(timer(operation, mode, "failure"));
            throw exception;
        }
    }

    /**
     * 작업 결과로 실행 모드를 판별할 수 있을 때 실행 시간을 기록합니다.
     *
     * entryToken 검증처럼 모드를 알기 전에 작업을 시작해야 하는 경우에 사용합니다.
     */
    public <T> T record(
            String operation,
            Supplier<T> supplier,
            Function<T, String> modeResolver
    ) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            T result = supplier.get();
            String mode = modeResolver.apply(result);
            recordEvent(operation, mode, "success");
            sample.stop(timer(operation, mode, "success"));
            return result;
        } catch (RuntimeException exception) {
            recordEvent(operation, "unknown", "failure");
            sample.stop(timer(operation, "unknown", "failure"));
            throw exception;
        }
    }

    public void record(String operation, String mode, Runnable runnable) {
        record(operation, mode, () -> {
            runnable.run();
            return null;
        });
    }

    private void recordEvent(String operation, String mode, String result) {
        meterRegistry.counter(
                EVENT_METRIC,
                "operation", operation,
                "mode", mode,
                "result", result
        ).increment();
    }

    private Timer timer(String operation, String mode, String result) {
        return Timer.builder(DURATION_METRIC)
                .tags(
                        "operation", operation,
                        "mode", mode,
                        "result", result
                )
                .publishPercentileHistogram()
                .register(meterRegistry);
    }
}
