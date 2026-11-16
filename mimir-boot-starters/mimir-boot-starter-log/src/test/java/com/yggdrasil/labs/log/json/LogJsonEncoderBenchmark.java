package com.yggdrasil.labs.log.json;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import com.yggdrasil.labs.log.json.LogFieldProviderRegistry.NamedProvider;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.status.Status;

/** 手工运行的完整编码链路基准；不设性能门槛，也不参与默认 *Test 测试集。 */
@SuppressWarnings("java:S3577") // 保留 Benchmark 命名以排除默认测试发现，仅通过 -Dtest 手工运行。
class LogJsonEncoderBenchmark {

    private static final String PREFIX = "mimir.boot.log.json.benchmark.";
    private static final String LOGGER_NAME = "benchmark.log";
    private static final String MESSAGE =
            "password=alpha token=beta secret=gamma api_key=delta payload=" + "x".repeat(967);
    private static final String PATTERN =
            "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %replace(%X{traceId}){'^(.+)$','[$1] '"
                + " }%replace(%X{spanId}){'^(.+)$','[$1] ' }%logger{50} - %mask%n%maskThrowable";

    @TempDir Path directory;

    @Test
    void measuresTextAndJsonWithProvidersAndBothOutputs() throws IOException, JoranException {
        int samples = Integer.getInteger(PREFIX + "samples", 3);
        int warmup = Integer.getInteger(PREFIX + "warmup", 5_000);
        int events = Integer.getInteger(PREFIX + "events", 20_000);
        assertThat(samples).isPositive();
        assertThat(warmup).isNotNegative();
        assertThat(events).isPositive();
        System.out.printf(
                "json-benchmark jvm=%s java=%s cpus=%d heapMiB=%d args=%s warmup=%d events=%d"
                    + " samples=%d messageBytes=%d latency=event-creation-to-write-and-flush-return"
                    + " throughput=measured-events-including-final-drain"
                    + " console=stdout-redirected-to-temporary-file%n",
                System.getProperty("java.vm.name"),
                System.getProperty("java.version"),
                Runtime.getRuntime().availableProcessors(),
                Runtime.getRuntime().maxMemory() / (1024 * 1024),
                ManagementFactory.getRuntimeMXBean().getInputArguments(),
                warmup,
                events,
                samples,
                MESSAGE.getBytes(StandardCharsets.UTF_8).length);

        try {
            // 每轮交替覆盖完整矩阵，避免某一组独占进程初期的 JIT 成本。
            for (int sample = 1; sample <= samples; sample++) {
                for (String format : List.of("text", "json")) {
                    for (int providers : new int[] {0, 3}) {
                        for (boolean asynchronous : new boolean[] {false, true}) {
                            measure(format, providers, asynchronous, sample, warmup, events);
                        }
                    }
                }
            }
        } finally {
            com.yggdrasil.labs.log.converter.SensitiveDataConverterInternals.resetForTest(
                    (LoggerContext) LoggerFactory.getILoggerFactory());
        }
    }

    private void measure(
            String format,
            int providerCount,
            boolean asynchronous,
            int sample,
            int warmup,
            int events)
            throws IOException, JoranException {
        String output = asynchronous ? "async-file" : "sync-console";
        Path file =
                directory.resolve(
                        format + "-" + providerCount + "-" + output + "-" + sample + ".log");
        LoggerContext context = new LoggerContext();
        context.setMDCAdapter(new LogbackMDCAdapter());
        Object owner = new Object();
        PrintStream original = System.out;
        long[] latencies = new long[events];
        Completion completion = new Completion(events);
        long elapsed;
        // 真正使用 ConsoleAppender；标准输出重定向到临时文件，避免终端渲染和测试报告洪泛。
        try (PrintStream redirected =
                new PrintStream(
                        asynchronous
                                ? OutputStream.nullOutputStream()
                                : Files.newOutputStream(file),
                        false,
                        StandardCharsets.UTF_8)) {
            LogFieldProviderRegistry.register(owner, providerCount == 0 ? List.of() : providers());
            if (!asynchronous) {
                System.setOut(redirected);
            }
            try {
                com.yggdrasil.labs.log.converter.SensitiveDataConverterInternals.resetForTest(null);
                configure(context, format, asynchronous, file);
                Logger logger = context.getLogger(LOGGER_NAME);
                context.getMDCAdapter().put("traceId", "4bf9");
                context.getMDCAdapter().put("spanId", "00f0");
                context.getMDCAdapter().put("tenantId", "tenant-1");
                for (int event = 0; event < warmup; event++) {
                    log(logger, completion, -1);
                }
                awaitWarmup(completion, warmup);
                long started = System.nanoTime();
                for (int event = 0; event < events; event++) {
                    latencies[event] = log(logger, completion, event);
                }
                // maxFlushTime=0 等待异步队列排空；吞吐包含排空与出口关闭，避免仅测量入队速度。
                context.stop();
                elapsed = System.nanoTime() - started;
            } finally {
                context.stop();
                System.setOut(original);
                LogFieldProviderRegistry.clear(owner);
            }
        }
        assertThat(context.getStatusManager().getCopyOfStatusList())
                .noneMatch(status -> status.getLevel() == Status.ERROR);
        assertThat(completion.completed.get()).isEqualTo(warmup + events);
        try (Stream<String> lines = Files.lines(file)) {
            assertThat(lines.count()).as("所有预热与测量事件都写出，不允许丢弃").isEqualTo((long) warmup + events);
        }
        Arrays.sort(latencies);
        Arrays.sort(completion.writeLatencies);
        System.out.printf(
                "json-benchmark format=%s providers=%d output=%s sample=%d events=%d"
                        + " throughput=%.2f events/s write-p95=%.3f us write-p99=%.3f us"
                        + " producer-p95=%.3f us producer-p99=%.3f us bytes=%d%n",
                format,
                providerCount,
                output,
                sample,
                events,
                events * 1_000_000_000.0 / elapsed,
                percentile(completion.writeLatencies, 0.95) / 1_000.0,
                percentile(completion.writeLatencies, 0.99) / 1_000.0,
                percentile(latencies, 0.95) / 1_000.0,
                percentile(latencies, 0.99) / 1_000.0,
                Files.size(file));
    }

    private static void configure(
            LoggerContext context, String format, boolean asynchronous, Path file)
            throws JoranException {
        String target =
                asynchronous
                        ? "<appender name=\"OUTPUT\" class=\""
                                + MeasuredFileAppender.class.getName()
                                + "\"><file>"
                                + xml(file.toString())
                                + "</file><append>false</append>"
                        : "<appender name=\"OUTPUT\" class=\""
                                + MeasuredConsoleAppender.class.getName()
                                + "\">";
        if (asynchronous) {
            target +=
                    "<rollingPolicy"
                        + " class=\"ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy\">"
                        + "<fileNamePattern>"
                            + xml(
                                    file.getParent()
                                            .resolve("%d{yyyy-MM,aux}")
                                            .resolve(
                                                    file.getFileName().toString()
                                                            + "-%d{yyyy-MM-dd}-%i.log.gz")
                                            .toString())
                            + "</fileNamePattern><maxFileSize>100MB</maxFileSize><maxHistory>30</maxHistory>"
                            + "<totalSizeCap>10GB</totalSizeCap></rollingPolicy>";
        }
        String async =
                asynchronous
                        ? "<appender name=\"ASYNC\" class=\"ch.qos.logback.classic.AsyncAppender\">"
                              + "<queueSize>1024</queueSize><discardingThreshold>0</discardingThreshold>"
                              + "<neverBlock>false</neverBlock><maxFlushTime>0</maxFlushTime><appender-ref"
                              + " ref=\"OUTPUT\"/></appender>"
                        : "";
        String configuration =
                "<configuration><conversionRule conversionWord=\"mask\""
                    + " class=\"com.yggdrasil.labs.log.converter.SensitiveDataConverter\"/><conversionRule"
                    + " conversionWord=\"maskThrowable\""
                    + " class=\"com.yggdrasil.labs.log.converter.SensitiveThrowableProxyConverter\"/><property"
                    + " scope=\"context\" name=\""
                        + LogJsonKeys.FORMAT
                        + "\" value=\""
                        + format
                        + "\"/>"
                        + "<property scope=\"context\" name=\""
                        + LogJsonKeys.SERVICE
                        + "\" value=\"benchmark\"/><property scope=\"context\""
                        + " name=\"mimir.boot.log.mask.enabledPatterns\""
                        + " value=\"password,token,secret,api_key\"/><appender name=\"FIELDS\""
                        + " class=\"com.yggdrasil.labs.log.json.LogFieldCaptureAppender\"><active>"
                        + "json".equals(format)
                        + "</active></appender>"
                        + target
                        + "<encoder"
                        + " class=\"com.yggdrasil.labs.log.json.FormatDelegatingEncoder\"><pattern>"
                        + PATTERN
                        + "</pattern><charset>UTF-8</charset></encoder></appender>"
                        + async
                        + "<logger name=\""
                        + LOGGER_NAME
                        + "\" level=\"INFO\" additivity=\"false\">"
                        + "<appender-ref ref=\"FIELDS\"/><appender-ref ref=\""
                        + (asynchronous ? "ASYNC" : "OUTPUT")
                        + "\"/></logger></configuration>";
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(
                new ByteArrayInputStream(configuration.getBytes(StandardCharsets.UTF_8)));
    }

    private static long log(Logger logger, Completion completion, int index) {
        long before = System.nanoTime();
        BenchmarkEvent event = new BenchmarkEvent(logger, before, completion, index);
        event.addKeyValuePair(new org.slf4j.event.KeyValuePair("orderId", 7));
        event.addKeyValuePair(new org.slf4j.event.KeyValuePair("ok", true));
        logger.callAppenders(event);
        return System.nanoTime() - before;
    }

    private static List<NamedProvider> providers() {
        return List.of(
                new NamedProvider("alpha", event -> Map.of("region", "east")),
                new NamedProvider("beta", event -> Map.of("business", List.of(1, 2, 3))),
                new NamedProvider(
                        "gamma", event -> Map.of("metadata", Map.of("source", "benchmark"))));
    }

    private static void awaitWarmup(Completion completion, int warmup) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (completion.completed.get() < warmup) {
            if (System.nanoTime() >= deadline) {
                throw new IllegalStateException("基准预热事件未在 30 秒内排空");
            }
            LockSupport.parkNanos(TimeUnit.MICROSECONDS.toNanos(100));
        }
    }

    private static long percentile(long[] sorted, double quantile) {
        return sorted[Math.min(sorted.length - 1, (int) Math.ceil(quantile * sorted.length) - 1)];
    }

    private static String xml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** 只在基准内观测真实控制台写入与 flush 返回的时刻。 */
    public static class MeasuredConsoleAppender extends ConsoleAppender<ILoggingEvent> {
        @Override
        protected void subAppend(ILoggingEvent event) {
            super.subAppend(event);
            completed(event);
        }
    }

    /** 只在基准内观测异步线程的真实文件写入与 flush 返回的时刻。 */
    public static class MeasuredFileAppender extends RollingFileAppender<ILoggingEvent> {
        @Override
        protected void subAppend(ILoggingEvent event) {
            super.subAppend(event);
            completed(event);
        }
    }

    private static void completed(ILoggingEvent event) {
        if (event instanceof BenchmarkEvent timed) {
            if (timed.index >= 0) {
                timed.completion.writeLatencies[timed.index] = System.nanoTime() - timed.started;
            }
            timed.completion.completed.incrementAndGet();
        }
    }

    private static final class Completion {
        private final AtomicInteger completed = new AtomicInteger();
        private final long[] writeLatencies;

        private Completion(int events) {
            writeLatencies = new long[events];
        }
    }

    private static final class BenchmarkEvent extends LoggingEvent {
        private final long started;
        private final Completion completion;
        private final int index;

        private BenchmarkEvent(Logger logger, long started, Completion completion, int index) {
            super(Logger.class.getName(), logger, Level.INFO, MESSAGE, null, null);
            this.started = started;
            this.completion = completion;
            this.index = index;
        }
    }
}
