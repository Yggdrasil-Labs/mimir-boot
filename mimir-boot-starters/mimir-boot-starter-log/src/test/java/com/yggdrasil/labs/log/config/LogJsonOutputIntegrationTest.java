package com.yggdrasil.labs.log.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yggdrasil.labs.log.converter.SensitiveDataConverter;
import com.yggdrasil.labs.log.field.LogEventView;
import com.yggdrasil.labs.log.field.LogFieldProvider;
import com.yggdrasil.labs.log.json.LogFieldProviderRegistry;
import com.yggdrasil.labs.log.web.AccessLogFilter;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.status.Status;

/** JSON 真实 XML 接入、字段治理与异步出口的端到端契约。 */
@ExtendWith(OutputCaptureExtension.class)
class LogJsonOutputIntegrationTest {

    private static final String CONTAINER =
            "classpath:com/yggdrasil/labs/log/logback/container.xml";
    private static final String SERVER = "classpath:com/yggdrasil/labs/log/logback/server.xml";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SECRETS = "password=alpha token=beta secret=gamma api_key=delta";

    @TempDir Path logPath;
    private String id;

    @BeforeEach
    void setUp() {
        MaskTestSupport.reset();
        MDC.clear();
        id = UUID.randomUUID().toString();
        assertThat(LogFieldProviderRegistry.current()).isEmpty();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        loggerContext().stop();
        MaskTestSupport.reset();
    }

    @Test
    void container_emits_full_envelope_and_excludes_mdc_keys(CapturedOutput output)
            throws Exception {
        try (ConfigurableApplicationContext ignored =
                startJson(
                        CONTAINER,
                        "--spring.profiles.active=prod",
                        "--mimir.boot.log.instance=order-01",
                        "--mimir.boot.log.json.exclude-mdc-keys[0]=internalDebug")) {
            MDC.setContextMap(
                    Map.of(
                            "traceId",
                            "4bf9",
                            "spanId",
                            "00f0",
                            "tenantId",
                            "t1",
                            "internalDebug",
                            "1"));
            LoggerFactory.getLogger("com.example.OrderService").info("开始处理请求 {}", id);
        }
        JsonNode event = event(output, "开始处理请求 " + id);
        assertThat(event.path("service").asText()).isEqualTo("order-service");
        assertThat(event.path("environment").asText()).isEqualTo("prod");
        assertThat(event.path("instance").asText()).isEqualTo("order-01");
        assertThat(event.path("traceId").asText()).isEqualTo("4bf9");
        assertThat(event.path("spanId").asText()).isEqualTo("00f0");
        assertThat(event.path("context")).isEqualTo(JSON.readTree("{\"tenantId\":\"t1\"}"));
        assertThat(event.path("logger").asText()).isEqualTo("com.example.OrderService");
        assertThat(event.path("message").asText()).isEqualTo("开始处理请求 " + id);
        assertThat(event.path("thread").asText()).isEqualTo(Thread.currentThread().getName());
        assertThat(event.path("level").asText()).isEqualTo("INFO");
        assertThat(OffsetDateTime.parse(event.path("timestamp").asText())).isNotNull();
        assertAbsent(event, "attributes", "exception", "processing");
        assertNoFiles();
    }

    @Test
    void optional_fields_are_omitted_and_explicit_environment_wins(CapturedOutput output)
            throws Exception {
        try (ConfigurableApplicationContext ignored =
                start(
                        CONTAINER,
                        false,
                        "--mimir.boot.log.format=json",
                        "--spring.profiles.active=prod,east")) {
            LoggerFactory.getLogger("demo.json").info("optional {}", id);
            MDC.setContextMap(Map.of("traceId", "", "spanId", "", "tenantId", "t1"));
            LoggerFactory.getLogger("demo.json").info("empty-trace {}", id);
        }
        JsonNode optional = event(output, "optional " + id);
        assertThat(optional.path("service").asText()).isEqualTo("application");
        assertAbsent(
                optional,
                "environment",
                "instance",
                "traceId",
                "spanId",
                "context",
                "attributes",
                "exception",
                "processing");
        JsonNode emptyTrace = event(output, "empty-trace " + id);
        assertAbsent(emptyTrace, "traceId", "spanId");
        assertThat(emptyTrace.path("context")).isEqualTo(JSON.readTree("{\"tenantId\":\"t1\"}"));
        MDC.clear();
        try (ConfigurableApplicationContext ignored =
                startJson(
                        CONTAINER,
                        "--spring.profiles.active=prod,east",
                        "--mimir.boot.log.environment=prod-east")) {
            LoggerFactory.getLogger("demo.json").info("explicit-environment {}", id);
        }
        assertThat(event(output, "explicit-environment " + id).path("environment").asText())
                .isEqualTo("prod-east");
    }

    @Test
    void special_characters_and_exception_remain_one_json_event(CapturedOutput output)
            throws Exception {
        String message = "第一行\n第二行 \"引号\" \t " + id;
        try (ConfigurableApplicationContext ignored = startJson(CONTAINER)) {
            LoggerFactory.getLogger("demo.json")
                    .error(message, new IllegalStateException("bad", new IOException("io")));
        }
        JsonNode event = event(output, message);
        assertThat(event.path("message").asText()).isEqualTo(message);
        assertThat(event.at("/exception/type").asText())
                .isEqualTo("java.lang.IllegalStateException");
        assertThat(event.at("/exception/message").asText()).isEqualTo("bad");
        assertThat(event.at("/exception/stackTrace").asText())
                .contains("java.lang.IllegalStateException", "java.io.IOException", "Caused by");
    }

    @Test
    void server_routes_json_events_and_captures_provider_once_on_producer_thread(
            CapturedOutput output) throws Exception {
        ProbeProvider provider;
        try (ConfigurableApplicationContext context = startJson(SERVER)) {
            provider = context.getBean(ProbeProvider.class);
            LoggerFactory.getLogger("demo.json").info("business-info {}", id);
            LoggerFactory.getLogger("demo.json").error("provider-event {}", id);
            LoggerFactory.getLogger("access.log").info("access-event {}", id);
            LoggerFactory.getLogger("SQL.JSON").info("sql-event {}", id);
        }
        loggerContext().stop();
        Path directory = logPath.resolve("order-service");
        List<JsonNode> info = readFile(directory.resolve("info.log"));
        List<JsonNode> errors = readFile(directory.resolve("error.log"));
        JsonNode infoError = event(info, "provider-event " + id);
        assertThat(event(errors, "provider-event " + id)).isEqualTo(infoError);
        assertThat(infoError.at("/attributes/orderId").asText()).isEqualTo("o-1");
        assertThat(infoError.at("/attributes/items")).isEqualTo(JSON.readTree("[1,2]"));
        assertThat(provider.calls.get()).isEqualTo(1);
        assertThat(provider.thread.get()).isEqualTo(Thread.currentThread().getName());
        assertThat(event(info, "business-info " + id).path("level").asText()).isEqualTo("INFO");
        assertThat(errors)
                .noneMatch(node -> node.path("message").asText().contains("business-info " + id));
        assertThat(info)
                .noneMatch(
                        node ->
                                node.path("logger").asText().equals("access.log")
                                        || node.path("logger").asText().startsWith("SQL."));
        event(readFile(directory.resolve("access.log")), "access-event " + id);
        event(readFile(directory.resolve("sql.log")), "sql-event " + id);
        assertThat(output.getOut()).doesNotContain(id);
    }

    @Test
    void access_filter_emits_typed_success_failure_and_timeout_fields(CapturedOutput output)
            throws Exception {
        try (ConfigurableApplicationContext ignored = startJson(CONTAINER)) {
            AccessLogFilter filter = new AccessLogFilter(3000, List.of());
            MockHttpServletRequest request = request("/orders/" + id);
            request.setQueryString("token=x");
            filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});

            MockHttpServletResponse failureResponse = new MockHttpServletResponse();
            failureResponse.setStatus(500);
            assertThatThrownBy(
                            () ->
                                    filter.doFilter(
                                            request("/failure/" + id),
                                            failureResponse,
                                            (req, res) -> {
                                                throw new IllegalStateException("bad");
                                            }))
                    .isInstanceOf(IllegalStateException.class);

            MockHttpServletRequest timeoutRequest = request("/timeout/" + id);
            timeoutRequest.setAsyncSupported(true);
            MockHttpServletResponse timeoutResponse = new MockHttpServletResponse();
            timeoutResponse.setStatus(503);
            filter.doFilter(
                    timeoutRequest, timeoutResponse, (req, res) -> req.startAsync(req, res));
            MockAsyncContext async = (MockAsyncContext) timeoutRequest.getAsyncContext();
            for (AsyncListener listener : List.copyOf(async.getListeners())) {
                listener.onTimeout(new AsyncEvent(async, timeoutRequest, timeoutResponse));
                listener.onComplete(new AsyncEvent(async, timeoutRequest, timeoutResponse));
            }
        }
        JsonNode success = event(output, "/orders/" + id);
        JsonNode attributes = success.path("attributes");
        assertThat(success.path("logger").asText()).isEqualTo("access.log");
        assertThat(success.path("level").asText()).isEqualTo("INFO");
        assertThat(attributes.path("method").asText()).isEqualTo("GET");
        assertThat(attributes.path("path").asText()).isEqualTo("/orders/" + id);
        assertThat(attributes.path("statusCode").isIntegralNumber()).isTrue();
        assertThat(attributes.path("statusCode").intValue()).isEqualTo(200);
        assertThat(attributes.path("durationMs").isIntegralNumber()).isTrue();
        assertThat(attributes.path("durationMs").longValue()).isGreaterThanOrEqualTo(0);
        assertThat(attributes.path("clientIp").asText()).isEqualTo("192.0.2.10");
        assertThat(attributes.path("userAgent").asText()).isEqualTo("example-client");
        assertThat(attributes.path("outcome").asText()).isEqualTo("COMPLETED");
        assertThat(attributes.path("slow").isBoolean()).isTrue();
        assertThat(attributes.path("slow").booleanValue()).isFalse();
        assertAbsent(attributes, "errorType");
        assertThat(success.path("message").asText())
                .isEqualTo(
                        "IP=[192.0.2.10], Method=[GET], URI=[/orders/"
                                + id
                                + "], Status=[200], Outcome=[COMPLETED], ErrorType=[-], Duration=["
                                + attributes.path("durationMs").longValue()
                                + "ms], UserAgent=[example-client]")
                .doesNotContain("token=x");
        JsonNode failure = event(output, "/failure/" + id);
        assertThat(failure.path("level").asText()).isEqualTo("ERROR");
        assertThat(failure.at("/attributes/outcome").asText()).isEqualTo("ERROR");
        assertThat(failure.at("/attributes/errorType").asText())
                .isEqualTo("java.lang.IllegalStateException");
        JsonNode timeout = event(output, "/timeout/" + id);
        assertThat(timeout.path("level").asText()).isEqualTo("ERROR");
        assertThat(timeout.at("/attributes/statusCode").intValue()).isEqualTo(503);
        assertThat(timeout.at("/attributes/outcome").asText()).isEqualTo("TIMEOUT");
        assertThat(timeout.at("/attributes/errorType").asText()).isEqualTo("ASYNC_TIMEOUT");
        assertAbsent(timeout, "exception");
    }

    @Test
    void sql_json_message_stays_a_string_with_trace_id(CapturedOutput output) throws Exception {
        String sql = "{\"sql\":\"select 1\",\"event\":\"" + id + "\"}";
        try (ConfigurableApplicationContext ignored = startJson(CONTAINER)) {
            MDC.put("traceId", "sql-trace");
            LoggerFactory.getLogger("SQL.SLOW").warn(sql);
        }
        JsonNode event = event(output, sql);
        assertThat(event.path("logger").asText()).isEqualTo("SQL.SLOW");
        assertThat(event.path("level").asText()).isEqualTo("WARN");
        assertThat(event.path("message").isTextual()).isTrue();
        assertThat(event.path("message").asText()).isEqualTo(sql);
        assertThat(event.path("traceId").asText()).isEqualTo("sql-trace");
    }

    @Test
    void custom_xml_uses_public_support_and_capture_contract(CapturedOutput output)
            throws Exception {
        ProbeProvider provider;
        try (ConfigurableApplicationContext context =
                start(
                        "classpath:mimir-json-capture.xml",
                        "--mimir.boot.log.json.exclude-mdc-keys[0]=internalDebug",
                        "--mimir.boot.log.environment=custom")) {
            provider = context.getBean(ProbeProvider.class);
            MDC.setContextMap(Map.of("internalDebug", "1", "tenantId", "t1"));
            LoggerFactory.getLogger("demo.json").info("provider-event {}", id);
        }
        JsonNode event = event(output, "provider-event " + id);
        assertThat(event.path("service").asText()).isEqualTo("order-service");
        assertThat(event.path("environment").asText()).isEqualTo("custom");
        assertThat(event.path("context")).isEqualTo(JSON.readTree("{\"tenantId\":\"t1\"}"));
        assertThat(event.at("/attributes/orderId").asText()).isEqualTo("o-1");
        assertThat(provider.calls.get()).isEqualTo(1);
        assertAbsent(event, "processing");
    }

    @Test
    void custom_xml_without_capture_does_not_call_providers(CapturedOutput output)
            throws Exception {
        ProbeProvider provider;
        try (ConfigurableApplicationContext context =
                start("classpath:mimir-json-no-capture.xml")) {
            provider = context.getBean(ProbeProvider.class);
            LoggerFactory.getLogger("demo.json").info("provider-event {}", id);
        }
        JsonNode event = event(output, "provider-event " + id);
        assertAbsent(event, "attributes", "processing");
        assertThat(provider.calls.get()).isZero();
    }

    @Test
    void early_default_rules_and_programmatic_patterns_both_apply(CapturedOutput output)
            throws Exception {
        try (ConfigurableApplicationContext ignored = startJson(CONTAINER)) {
            SensitiveDataConverter.addCustomPattern("ord-\\d+");
            MDC.put("userPassword", "abc");
            LoggerFactory.getLogger("demo.json")
                    .atInfo()
                    .addKeyValue("orderId", "ord-123")
                    .addKeyValue("token", 123)
                    .log("programmatic {} ord-123 password=alpha", id);
        }
        assertThat(event(output, "early " + id).path("message").asText())
                .isEqualTo("early " + id + " password=**** token=**** secret=**** api_key=****");
        JsonNode event = event(output, "programmatic " + id);
        assertThat(event.path("message").asText())
                .isEqualTo("programmatic " + id + " **** password=****");
        assertThat(event.at("/context/userPassword").asText()).isEqualTo("****");
        assertThat(event.at("/attributes/orderId").asText()).isEqualTo("****");
        assertThat(event.at("/attributes/token").asText()).isEqualTo("****");
    }

    @Test
    void json_settings_keep_startup_values_until_restart(CapturedOutput output) throws Exception {
        String key = "mimir.boot.log.json.exclude-mdc-keys[0]";
        try (ConfigurableApplicationContext context = startJson(CONTAINER, "--" + key + "=a")) {
            context.getEnvironment()
                    .getPropertySources()
                    .addFirst(new MapPropertySource("runtime-json-settings", Map.of(key, "b")));
            assertThat(context.getEnvironment().getProperty(key)).isEqualTo("b");
            MDC.setContextMap(Map.of("a", "1", "b", "2"));
            LoggerFactory.getLogger("demo.json").info("before-restart {}", id);
        }
        assertThat(event(output, "before-restart " + id).path("context"))
                .isEqualTo(JSON.readTree("{\"b\":\"2\"}"));
        try (ConfigurableApplicationContext ignored = startJson(CONTAINER, "--" + key + "=b")) {
            LoggerFactory.getLogger("demo.json").info("after-restart {}", id);
        }
        assertThat(event(output, "after-restart " + id).path("context"))
                .isEqualTo(JSON.readTree("{\"a\":\"1\"}"));
    }

    @Test
    void queued_error_keeps_both_original_values_and_rules(CapturedOutput output) throws Exception {
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        ProbeProvider provider;
        try (ConfigurableApplicationContext context = startJson(SERVER);
                BlockedAsyncSink ignoredInfo = blockAsyncSink("ASYNC_FILE_INFO", entered, release);
                BlockedAsyncSink ignoredError =
                        blockAsyncSink("ASYNC_FILE_ERROR", entered, release)) {
            provider = context.getBean(ProbeProvider.class);
            LoggerFactory.getLogger("demo.json").error("queue-gate {}", id);
            assertThat(entered.await(5, TimeUnit.SECONDS)).as("两个异步出口均阻塞于先行事件").isTrue();
            Map<String, Object> mutable = new LinkedHashMap<>();
            mutable.put("n", 1);
            LoggerFactory.getLogger("demo.json")
                    .atError()
                    .addKeyValue("payload", mutable)
                    .addKeyValue("order", "ord-123")
                    .log("provider-event queued {} ord-123 password=alpha", id);
            mutable.put("n", 2);
            SensitiveDataConverter.addCustomPattern("ord-\\d+");
            release.countDown();
        } finally {
            release.countDown();
            loggerContext().stop();
        }
        Path directory = logPath.resolve("order-service");
        JsonNode info =
                event(readFile(directory.resolve("info.log")), "provider-event queued " + id);
        JsonNode error =
                event(readFile(directory.resolve("error.log")), "provider-event queued " + id);
        assertThat(error).isEqualTo(info);
        assertThat(info.path("message").asText())
                .isEqualTo("provider-event queued " + id + " ord-123 password=****");
        assertThat(info.at("/attributes/order").asText()).isEqualTo("ord-123");
        assertThat(info.at("/attributes/payload/n").intValue()).isEqualTo(1);
        assertThat(provider.calls.get()).isEqualTo(1);
        assertThat(provider.thread.get()).isEqualTo(Thread.currentThread().getName());
        assertThat(output.getOut()).doesNotContain(id);
        try (ConfigurableApplicationContext ignored = startJson(CONTAINER)) {
            LoggerFactory.getLogger("demo.json").info("after-queued-restart {}", id);
        }
        event(output, "after-queued-restart " + id);
    }

    @Test
    void released_async_gate_writes_event_even_when_writer_is_interrupted() throws Exception {
        String message = "queue-gate interrupted " + id;
        try (ConfigurableApplicationContext ignored = startJson(SERVER);
                BlockedAsyncSink blocker =
                        blockAsyncSink(
                                "ASYNC_FILE_INFO", new CountDownLatch(1), new CountDownLatch(0))) {
            AtomicReference<Boolean> interrupted = new AtomicReference<>();
            Thread writer =
                    new Thread(
                            () -> {
                                Thread.currentThread().interrupt();
                                blocker.doAppend(
                                        new LoggingEvent(
                                                getClass().getName(),
                                                loggerContext().getLogger("demo.json"),
                                                Level.INFO,
                                                message,
                                                null,
                                                null));
                                interrupted.set(Thread.currentThread().isInterrupted());
                            },
                            "interrupted-async-sink");
            writer.start();
            writer.join(TimeUnit.SECONDS.toMillis(5));
            assertThat(writer.isAlive()).as("已释放的闸门不应阻塞写出").isFalse();
            assertThat(interrupted.get()).as("写出后保留调用线程的中断状态").isTrue();
            assertThat(loggerContext().getStatusManager().getCopyOfStatusList())
                    .noneMatch(status -> status.getLevel() == Status.ERROR);
            event(readFile(logPath.resolve("order-service/info.log")), message);
        } finally {
            loggerContext().stop();
        }
    }

    @Test
    void failed_queue_assertion_drains_both_sinks_before_restart(CapturedOutput output)
            throws Exception {
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AssertionError expected = new AssertionError("模拟排队期间断言失败");
        String message = "cleanup-after-failure " + id;
        assertThatThrownBy(
                        () -> {
                            try (ConfigurableApplicationContext ignored = startJson(SERVER);
                                    BlockedAsyncSink ignoredInfo =
                                            blockAsyncSink("ASYNC_FILE_INFO", entered, release);
                                    BlockedAsyncSink ignoredError =
                                            blockAsyncSink("ASYNC_FILE_ERROR", entered, release)) {
                                LoggerFactory.getLogger("demo.json").error("queue-gate {}", id);
                                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                                LoggerFactory.getLogger("demo.json").error(message);
                                throw expected;
                            } finally {
                                release.countDown();
                                loggerContext().stop();
                            }
                        })
                .isSameAs(expected)
                .satisfies(ex -> assertThat(ex.getSuppressed()).isEmpty());
        Path directory = logPath.resolve("order-service");
        assertThat(event(readFile(directory.resolve("info.log")), message))
                .isEqualTo(event(readFile(directory.resolve("error.log")), message));
        try (ConfigurableApplicationContext ignored = startJson(CONTAINER)) {
            LoggerFactory.getLogger("demo.json").info("after-failed-queue-restart {}", id);
        }
        event(output, "after-failed-queue-restart " + id);
    }

    @Test
    void async_sink_failure_is_reported_after_real_delegate_drains() throws Exception {
        IllegalStateException expected = new IllegalStateException("模拟异步写出失败");
        try (ConfigurableApplicationContext ignored = startJson(SERVER)) {
            assertThatThrownBy(
                            () -> {
                                try (BlockedAsyncSink sink =
                                        blockAsyncSink(
                                                "ASYNC_FILE_INFO",
                                                new CountDownLatch(1),
                                                new CountDownLatch(0))) {
                                    LoggingEvent broken =
                                            new LoggingEvent() {
                                                @Override
                                                public void prepareForDeferredProcessing() {
                                                    // 由真实异步 worker 触发下方故障。
                                                }

                                                @Override
                                                public String getFormattedMessage() {
                                                    throw expected;
                                                }
                                            };
                                    broken.setLevel(Level.ERROR);
                                    sink.async.doAppend(broken);
                                }
                            })
                    .isInstanceOf(AssertionError.class)
                    .hasCause(expected);
            event(readFile(logPath.resolve("order-service/info.log")), "queue-drained");
            assertThat(loggerContext().getStatusManager().getCopyOfStatusList())
                    .noneMatch(status -> status.getLevel() == Status.ERROR);
        } finally {
            loggerContext().stop();
        }
    }

    private static BlockedAsyncSink blockAsyncSink(
            String appenderName, CountDownLatch entered, CountDownLatch release) {
        AsyncAppender async =
                (AsyncAppender)
                        loggerContext()
                                .getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
                                .getAppender(appenderName);
        var delegate = async.iteratorForAppenders().next();
        async.detachAppender(delegate);
        // AsyncAppender 的 detach 不重置单下游计数，沿用队列集成测试的替换方式。
        org.springframework.test.util.ReflectionTestUtils.setField(async, "appenderCount", 0);
        BlockedAsyncSink blocker = new BlockedAsyncSink(async, delegate, entered, release);
        blocker.setContext(loggerContext());
        blocker.start();
        async.addAppender(blocker);
        return blocker;
    }

    private static final class BlockedAsyncSink extends UnsynchronizedAppenderBase<ILoggingEvent>
            implements AutoCloseable {
        private final AsyncAppender async;
        private final Appender<ILoggingEvent> delegate;
        private final CountDownLatch entered;
        private final CountDownLatch release;
        private final CountDownLatch drained = new CountDownLatch(1);
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private final ILoggingEvent drainMarker =
                new LoggingEvent(
                        getClass().getName(),
                        loggerContext().getLogger("demo.json"),
                        Level.ERROR,
                        "queue-drained",
                        null,
                        null);

        private BlockedAsyncSink(
                AsyncAppender async,
                Appender<ILoggingEvent> delegate,
                CountDownLatch entered,
                CountDownLatch release) {
            this.async = async;
            this.delegate = delegate;
            this.entered = entered;
            this.release = release;
        }

        @Override
        protected void append(ILoggingEvent event) {
            try {
                if (event.getFormattedMessage().startsWith("queue-gate ")) {
                    entered.countDown();
                    await(release, "异步写出闸门超时");
                }
                delegate.doAppend(event);
            } catch (RuntimeException | AssertionError ex) {
                failure.compareAndSet(null, ex);
            } finally {
                if (event == drainMarker) {
                    drained.countDown();
                }
            }
        }

        @Override
        public void close() {
            release.countDown();
            // 标记经同一 FIFO 和真实 delegate 写出后，才能关闭 Spring / Logback。
            async.doAppend(drainMarker);
            await(drained, "异步出口未完成排空: " + async.getName());
            if (failure.get() != null) {
                throw new AssertionError("异步出口写出失败: " + async.getName(), failure.get());
            }
            assertThat(loggerContext().getStatusManager().getCopyOfStatusList())
                    .noneMatch(status -> status.getLevel() == Status.ERROR);
        }

        @Override
        public void stop() {
            delegate.stop();
            super.stop();
        }

        private static void await(CountDownLatch latch, String message) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            boolean interrupted = false;
            try {
                while (latch.getCount() != 0) {
                    try {
                        long remaining = deadline - System.nanoTime();
                        if (remaining <= 0 || !latch.await(remaining, TimeUnit.NANOSECONDS)) {
                            throw new IllegalStateException(message);
                        }
                    } catch (InterruptedException ex) {
                        interrupted = true;
                    }
                }
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private ConfigurableApplicationContext startJson(String config, String... extra) {
        String[] args = new String[extra.length + 1];
        args[0] = "--mimir.boot.log.format=json";
        System.arraycopy(extra, 0, args, 1, extra.length);
        return start(config, args);
    }

    private ConfigurableApplicationContext start(String config, String... extra) {
        return start(config, true, extra);
    }

    private ConfigurableApplicationContext start(
            String config, boolean withApplicationName, String... extra) {
        SpringApplication application = new SpringApplication(App.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(
                withApplicationName
                        ? Map.of(
                                "spring.application.name",
                                "order-service",
                                "spring.main.banner-mode",
                                "off")
                        : Map.of("spring.main.banner-mode", "off"));
        application.addListeners(
                (ApplicationPreparedEvent event) ->
                        LoggerFactory.getLogger("demo.early").info("early {} {}", id, SECRETS));
        String[] args = new String[extra.length + 2];
        args[0] = "--logging.config=" + config;
        args[1] = "--logging.file.path=" + logPath;
        System.arraycopy(extra, 0, args, 2, extra.length);
        return application.run(args);
    }

    private static LoggerContext loggerContext() {
        return (LoggerContext) LoggerFactory.getILoggerFactory();
    }

    private static MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRemoteAddr("192.0.2.10");
        request.addHeader("User-Agent", "example-client");
        return request;
    }

    private static JsonNode event(CapturedOutput output, String marker) throws IOException {
        List<JsonNode> events = new ArrayList<>();
        for (String line :
                output.getOut().lines().filter(value -> value.startsWith("{")).toList()) {
            events.add(parseLine(line));
        }
        return event(events, marker);
    }

    private static JsonNode event(List<JsonNode> events, String marker) {
        List<JsonNode> matches =
                events.stream()
                        .filter(node -> node.path("message").asText().contains(marker))
                        .toList();
        assertThat(matches).as("每事件恰好一行 JSON: " + marker).hasSize(1);
        return matches.get(0);
    }

    private static List<JsonNode> readFile(Path path) throws IOException {
        List<JsonNode> events = new ArrayList<>();
        for (String line : Files.readAllLines(path)) {
            if (!line.isBlank()) {
                events.add(parseLine(line));
            }
        }
        return events;
    }

    private static JsonNode parseLine(String line) throws IOException {
        JsonNode node =
                JSON.reader()
                        .with(
                                com.fasterxml.jackson.databind.DeserializationFeature
                                        .FAIL_ON_TRAILING_TOKENS)
                        .readTree(line);
        assertThat(node.isObject()).as(line).isTrue();
        return node;
    }

    private static void assertAbsent(JsonNode event, String... names) {
        for (String name : names) {
            assertThat(event.has(name)).as(name + " 应省略").isFalse();
        }
    }

    private void assertNoFiles() throws IOException {
        try (var files = Files.walk(logPath)) {
            assertThat(files.filter(Files::isRegularFile)).isEmpty();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({LogMaskAutoConfiguration.class, LogFieldAutoConfiguration.class})
    static class App {
        @Bean
        ProbeProvider probeProvider() {
            return new ProbeProvider();
        }
    }

    static class ProbeProvider implements LogFieldProvider {
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicReference<String> thread = new AtomicReference<>();

        @Override
        public Map<String, ?> provide(LogEventView event) {
            if (!event.message().orElse("").startsWith("provider-event ")) {
                return Map.of();
            }
            calls.incrementAndGet();
            thread.set(Thread.currentThread().getName());
            return Map.of("orderId", "o-1", "items", List.of(1, 2));
        }
    }
}
