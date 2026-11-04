package com.yggdrasil.labs.log.converter;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.yggdrasil.labs.test.base.BaseUnitTest;

@SuppressWarnings("java:S3577")
// 基准类由手工命令按原名发现，故意不符合普通 Surefire *Test 命名。
class SensitiveDataConverterBenchmark extends BaseUnitTest {

    private static final int WARMUP_ITERATIONS = 100_000;
    private static final int MEASUREMENT_ITERATIONS = 1_000_000;
    private static final int SAMPLE_COUNT = 3;
    private static final String SAMPLE_COUNT_PROPERTY = "mimir.boot.log.mask.benchmark.samples";
    private static final String ENFORCE_THRESHOLD_PROPERTY =
            "mimir.boot.log.mask.benchmark.enforce-threshold";
    private static final double MAX_AVERAGE_DELTA_NANOS = 20_000.0;

    @SuppressWarnings("java:S1068") // sink 仅作为 volatile 黑洞，阻止 JIT 消除测量路径。
    private static volatile int sink;

    private static final String MESSAGE =
            "password=benchmark-value token=benchmark-value accessKey=benchmark-value "
                    + "payload="
                    + "x".repeat(943);

    @Test
    void measuresFixedOneKiBMessage() {
        SensitiveDataConverter converter = new SensitiveDataConverter();
        int sampleCount = Integer.getInteger(SAMPLE_COUNT_PROPERTY, SAMPLE_COUNT);
        boolean enforceThreshold =
                Boolean.parseBoolean(System.getProperty(ENFORCE_THRESHOLD_PROPERTY, "true"));
        System.out.printf(
                "jvm-input-arguments=%s enforce-threshold=%s%n",
                ManagementFactory.getRuntimeMXBean().getInputArguments(), enforceThreshold);
        assertTrue(sampleCount > 0, "基准样本数必须为正数");
        List<Double> deltas = new ArrayList<>(sampleCount);

        for (int sample = 1; sample <= sampleCount; sample++) {
            SensitiveDataConverter.publishConfiguration(List.of(), List.of(), "****");
            double baseline = measure(converter);
            SensitiveDataConverter.publishConfiguration(
                    List.of("password", "token", "secret"), List.of(), "****");
            double candidate = measure(converter);
            double delta = candidate - baseline;
            deltas.add(delta);

            System.out.printf(
                    "sample=%d warmup=%d measurement=%d bytes=%d baseline=%.2f ns/op candidate=%.2f"
                            + " ns/op delta=%+.2f ns/op%n",
                    sample,
                    WARMUP_ITERATIONS,
                    MEASUREMENT_ITERATIONS,
                    MESSAGE.length(),
                    baseline,
                    candidate,
                    delta);
        }

        double averageDelta =
                deltas.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        double maxDelta = deltas.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
        System.out.printf(
                "samples=%d average-delta=%+.2f ns/op max-delta=%+.2f ns/op threshold=%.2f ns/op%n",
                sampleCount, averageDelta, maxDelta, MAX_AVERAGE_DELTA_NANOS);
        if (enforceThreshold) {
            assertTrue(
                    averageDelta <= MAX_AVERAGE_DELTA_NANOS,
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

    // ==================== v2.4.0 新预设四组规则对照（spec 推荐门槛：1KiB 吞吐 ≥80%、P99 ≤125%） ====================

    private static final List<String> PRESET_RULES =
            List.of("password", "token", "secret", "api_key");
    private static final int BATCH_SIZE = 1_000;
    private static final int BATCHES = 1_000;
    private static final String PRESET_ENFORCE_PROPERTY =
            "mimir.boot.log.mask.benchmark.enforce-preset-threshold";

    @Test
    void measuresPresetRulesAgainstDisabledRules() {
        SensitiveDataConverter converter = new SensitiveDataConverter();
        int sampleCount = Integer.getInteger(SAMPLE_COUNT_PROPERTY, SAMPLE_COUNT);
        boolean enforce =
                Boolean.parseBoolean(System.getProperty(PRESET_ENFORCE_PROPERTY, "false"));
        String hit1k =
                "password=alpha token=beta secret=gamma api_key=delta payload=" + "x".repeat(970);
        String miss1k = "order=1 user=2 payload=" + "y".repeat(1001);
        String hit64k = "password=alpha token=beta " + "z".repeat(64 * 1024 - 26);
        String stack = stackTraceText();
        System.out.printf(
                "jvm=%s %s cpus=%d heap-max=%dMiB input-arguments=%s%n",
                System.getProperty("java.vm.name"),
                System.getProperty("java.version"),
                Runtime.getRuntime().availableProcessors(),
                Runtime.getRuntime().maxMemory() / (1024 * 1024),
                ManagementFactory.getRuntimeMXBean().getInputArguments());

        for (String[] input :
                new String[][] {
                    {"1KiB-hit", hit1k},
                    {"1KiB-miss", miss1k},
                    {"64KiB-hit", hit64k},
                    {"stack", stack}
                }) {
            for (int sample = 1; sample <= sampleCount; sample++) {
                SensitiveDataConverter.publishConfiguration(List.of(), List.of(), "****");
                double[] off = measureBatches(converter, input[1]);
                SensitiveDataConverter.publishConfiguration(PRESET_RULES, List.of(), "****");
                double[] on = measureBatches(converter, input[1]);
                double throughputRatio = mean(off) / mean(on);
                double p99Ratio = percentile(on, 0.99) / percentile(off, 0.99);
                System.out.printf(
                        "input=%s bytes=%d sample=%d off-mean=%.1f ns/op on-mean=%.1f ns/op"
                                + " on-p95=%.1f on-p99=%.1f throughput-ratio=%.3f p99-ratio=%.3f%n",
                        input[0],
                        input[1].length(),
                        sample,
                        mean(off),
                        mean(on),
                        percentile(on, 0.95),
                        percentile(on, 0.99),
                        throughputRatio,
                        p99Ratio);
                if (enforce && input[0].startsWith("1KiB")) {
                    assertTrue(throughputRatio >= 0.80, input[0] + " 吞吐低于关闭规则的 80%");
                    assertTrue(p99Ratio <= 1.25, input[0] + " P99 高于关闭规则的 125%");
                }
            }
        }
        SensitiveDataConverter.publishConfiguration(List.of(), List.of(), "****");
    }

    /** 每批 BATCH_SIZE 次，返回各批平均 ns/op；黑洞只取结果长度，避免计入结果字符串的 hashCode 计算 */
    private double[] measureBatches(SensitiveDataConverter converter, String message) {
        for (int i = 0; i < 3; i++) {
            for (int iteration = 0; iteration < WARMUP_ITERATIONS / 10; iteration++) {
                sink ^= converter.maskSensitiveData(message).length();
            }
        }
        int batches = message.length() > 16 * 1024 ? BATCHES / 10 : BATCHES;
        double[] perOp = new double[batches];
        for (int batch = 0; batch < batches; batch++) {
            long startedAt = System.nanoTime();
            for (int iteration = 0; iteration < BATCH_SIZE; iteration++) {
                sink ^= converter.maskSensitiveData(message).length();
            }
            perOp[batch] = (System.nanoTime() - startedAt) / (double) BATCH_SIZE;
        }
        return perOp;
    }

    private static double mean(double[] values) {
        return Arrays.stream(values).average().orElseThrow();
    }

    private static double percentile(double[] values, double quantile) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[Math.min(sorted.length - 1, (int) Math.ceil(quantile * sorted.length) - 1)];
    }

    private static String stackTraceText() {
        StringWriter writer = new StringWriter();
        new IllegalStateException(
                        "outer password=alpha",
                        new IllegalArgumentException("cause token=beta secret=gamma"))
                .printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    // ==================== 完整日志链路对照：PatternLayout(%mask) + 同步文件 appender ====================

    @Test
    void measuresFullFileChainWithPresetRules() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("mask-chain-");
        java.nio.file.Path file = dir.resolve("chain.log");
        ch.qos.logback.classic.LoggerContext context = new ch.qos.logback.classic.LoggerContext();
        // Logback 1.5 独立 LoggerContext 须显式设置 MDC 适配器，否则 append 失败且无输出
        context.setMDCAdapter(new ch.qos.logback.classic.util.LogbackMDCAdapter());
        String xml =
                "<configuration>"
                        + "<conversionRule conversionWord=\"mask\" class=\""
                        + SensitiveDataConverter.class.getName()
                        + "\"/>"
                        + "<appender name=\"FILE\" class=\"ch.qos.logback.core.FileAppender\">"
                        + "<file>"
                        + file
                        + "</file><encoder><pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level"
                        + " %logger{50} - %mask%n"
                        + "</pattern><charset>UTF-8</charset></encoder></appender><logger"
                        + " name=\"bench.chain\" level=\"INFO\" additivity=\"false\"><appender-ref"
                        + " ref=\"FILE\"/></logger></configuration>";
        ch.qos.logback.classic.joran.JoranConfigurator configurator =
                new ch.qos.logback.classic.joran.JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(
                new java.io.ByteArrayInputStream(
                        xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        new ch.qos.logback.core.util.StatusPrinter2().printInCaseOfErrorsOrWarnings(context);
        ch.qos.logback.classic.Logger logger = context.getLogger("bench.chain");
        int sampleCount = Integer.getInteger(SAMPLE_COUNT_PROPERTY, SAMPLE_COUNT);
        String hit1k =
                "password=alpha token=beta secret=gamma api_key=delta payload=" + "x".repeat(970);
        String miss1k = "order=1 user=2 payload=" + "y".repeat(1001);
        try {
            for (String[] input : new String[][] {{"1KiB-hit", hit1k}, {"1KiB-miss", miss1k}}) {
                double[] throughputRatios = new double[sampleCount];
                double[] p99Ratios = new double[sampleCount];
                for (int sample = 1; sample <= sampleCount; sample++) {
                    SensitiveDataConverter.publishConfiguration(List.of(), List.of(), "****");
                    double[] off = measureChain(logger, input[1]);
                    SensitiveDataConverter.publishConfiguration(PRESET_RULES, List.of(), "****");
                    double[] on = measureChain(logger, input[1]);
                    System.out.printf(
                            "chain input=%s sample=%d off-mean=%.1f ns/op on-mean=%.1f ns/op"
                                    + " off-p99=%.1f on-p99=%.1f throughput-ratio=%.3f"
                                    + " p99-ratio=%.3f%n",
                            input[0],
                            sample,
                            mean(off),
                            mean(on),
                            percentile(off, 0.99),
                            percentile(on, 0.99),
                            mean(off) / mean(on),
                            percentile(on, 0.99) / percentile(off, 0.99));
                    throughputRatios[sample - 1] = mean(off) / mean(on);
                    p99Ratios[sample - 1] = percentile(on, 0.99) / percentile(off, 0.99);
                }
                // 门槛按各轮中位数判定，降低单轮文件 I/O 抖动的影响；逐轮数据均已输出
                double throughputMedian = percentile(throughputRatios, 0.5);
                double p99Median = percentile(p99Ratios, 0.5);
                System.out.printf(
                        "chain input=%s samples=%d median-throughput-ratio=%.3f"
                                + " median-p99-ratio=%.3f gate=throughput>=0.80,p99<=1.25%n",
                        input[0], sampleCount, throughputMedian, p99Median);
                if (Boolean.parseBoolean(System.getProperty(PRESET_ENFORCE_PROPERTY, "false"))) {
                    assertTrue(throughputMedian >= 0.80, input[0] + " 链路吞吐中位数低于关闭规则的 80%");
                    assertTrue(p99Median <= 1.25, input[0] + " 链路 P99 中位数高于关闭规则的 125%");
                }
            }
        } finally {
            SensitiveDataConverter.publishConfiguration(List.of(), List.of(), "****");
            context.stop();
            SensitiveDataConverter.resetForTest(
                    (ch.qos.logback.core.Context) org.slf4j.LoggerFactory.getILoggerFactory());
        }
        String written = java.nio.file.Files.readString(file);
        java.nio.file.Files.deleteIfExists(file);
        java.nio.file.Files.deleteIfExists(dir);
        assertTrue(written.contains("password=**** token=****"), "链路基准未实际执行脱敏，测量无效");
        assertTrue(written.contains("password=alpha"), "链路基准未包含关闭规则的对照输出，测量无效");
    }

    private double[] measureChain(ch.qos.logback.classic.Logger logger, String message) {
        for (int iteration = 0; iteration < 20_000; iteration++) {
            logger.info(message);
        }
        int batches = 300;
        double[] perOp = new double[batches];
        for (int batch = 0; batch < batches; batch++) {
            long startedAt = System.nanoTime();
            for (int iteration = 0; iteration < 100; iteration++) {
                logger.info(message);
            }
            perOp[batch] = (System.nanoTime() - startedAt) / 100.0;
        }
        return perOp;
    }
}
