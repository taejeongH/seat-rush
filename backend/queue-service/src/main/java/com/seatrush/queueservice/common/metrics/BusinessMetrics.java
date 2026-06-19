package com.seatrush.queueservice.common.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

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
