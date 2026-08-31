package com.yggdrasil.labs.log.converter;

import com.yggdrasil.labs.test.base.BaseUnitTest;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveDataConverterBenchmark extends BaseUnitTest {

    private static final int WARMUP_ITERATIONS = 100_000;
    private static final int MEASUREMENT_ITERATIONS = 1_000_000;
    private static final int SAMPLE_COUNT = 3;
    private static final String SAMPLE_COUNT_PROPERTY = "mimir.boot.log.mask.benchmark.samples";
    private static final String ENFORCE_THRESHOLD_PROPERTY = "mimir.boot.log.mask.benchmark.enforce-threshold";
    private static final double MAX_AVERAGE_DELTA_NANOS = 20_000.0;
    private static volatile int sink;
    private static final String MESSAGE = "password=benchmark-value token=benchmark-value accessKey=benchmark-value "
            + "payload=" + "x".repeat(943);

    @Test
    void measuresFixedOneKiBMessage() {
        SensitiveDataConverter converter = new SensitiveDataConverter();
        int sampleCount = Integer.getInteger(SAMPLE_COUNT_PROPERTY, SAMPLE_COUNT);
        boolean enforceThreshold = Boolean.parseBoolean(System.getProperty(ENFORCE_THRESHOLD_PROPERTY, "true"));
        System.out.printf("jvm-input-arguments=%s enforce-threshold=%s%n",
                ManagementFactory.getRuntimeMXBean().getInputArguments(), enforceThreshold);
        assertTrue(sampleCount > 0, "基准样本数必须为正数");
        List<Double> deltas = new ArrayList<>(sampleCount);

        for (int sample = 1; sample <= sampleCount; sample++) {
            SensitiveDataConverter.publishConfiguration(List.of(), List.of(), "****");
            double baseline = measure(converter);
            SensitiveDataConverter.publishConfiguration(List.of("password", "token", "secret"), List.of(), "****");
            double candidate = measure(converter);
            double delta = candidate - baseline;
            deltas.add(delta);

            System.out.printf("sample=%d warmup=%d measurement=%d bytes=%d baseline=%.2f ns/op candidate=%.2f ns/op delta=%+.2f ns/op%n",
                    sample, WARMUP_ITERATIONS, MEASUREMENT_ITERATIONS, MESSAGE.length(), baseline, candidate, delta);
        }

        double averageDelta = deltas.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        double maxDelta = deltas.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
        System.out.printf("samples=%d average-delta=%+.2f ns/op max-delta=%+.2f ns/op threshold=%.2f ns/op%n",
                sampleCount, averageDelta, maxDelta, MAX_AVERAGE_DELTA_NANOS);
        if (enforceThreshold) {
            assertTrue(averageDelta <= MAX_AVERAGE_DELTA_NANOS,
                    () -> "三次脱敏增量平均值超过 20µs: " + averageDelta + " ns/op");
        }
    }

    private double measure(SensitiveDataConverter converter) {
        run(converter, WARMUP_ITERATIONS);
        long startedAt = System.nanoTime();
        run(converter, MEASUREMENT_ITERATIONS);
        return (System.nanoTime() - startedAt) / (double) MEASUREMENT_ITERATIONS;
    }

    private void run(SensitiveDataConverter converter, int iterations) {
        for (int iteration = 0; iteration < iterations; iteration++) {
            sink ^= converter.maskSensitiveData(MESSAGE).hashCode();
        }
    }
}
