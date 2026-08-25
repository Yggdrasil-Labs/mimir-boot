package com.yggdrasil.labs.log.converter;

import com.yggdrasil.labs.test.base.BaseUnitTest;
import org.junit.jupiter.api.Test;

import java.util.List;

class SensitiveDataConverterBenchmark extends BaseUnitTest {

    private static final int WARMUP_ITERATIONS = 100_000;
    private static final int MEASUREMENT_ITERATIONS = 1_000_000;
    private static volatile int sink;
    private static final String MESSAGE = "password=benchmark-value token=benchmark-value accessKey=benchmark-value "
            + "payload=" + "x".repeat(943);

    @Test
    void measuresFixedOneKiBMessage() {
        SensitiveDataConverter converter = new SensitiveDataConverter();

        SensitiveDataConverter.publishConfiguration(List.of(), List.of(), "****");
        double baseline = measure(converter);
        SensitiveDataConverter.publishConfiguration(List.of("password", "token", "secret"), List.of(), "****");
        double candidate = measure(converter);
        double delta = candidate - baseline;

        System.out.printf("warmup=%d measurement=%d bytes=%d baseline=%.2f ns/op candidate=%.2f ns/op delta=%+.2f ns/op%n",
                WARMUP_ITERATIONS, MEASUREMENT_ITERATIONS, MESSAGE.length(), baseline, candidate, delta);
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
