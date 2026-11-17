package com.yggdrasil.labs.log.json;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.event.KeyValuePair;
import org.slf4j.spi.LoggingEventBuilder;

import com.yggdrasil.labs.log.converter.SensitiveDataConverter;
import com.yggdrasil.labs.log.field.LogEventView;
import com.yggdrasil.labs.log.field.LogFieldProvider;
import com.yggdrasil.labs.log.json.LogFieldProviderRegistry.NamedProvider;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.encoder.JsonEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.core.spi.FilterReply;

class LogFieldCaptureAppenderTest {

    private final Object owner = new Object();
    private LoggerContext context;
    private LogFieldCaptureAppender appender;

    @BeforeEach
    void setUp() {
        LogFieldProviderRegistry.register(owner, List.of());
        context = new LoggerContext();
        context.putProperty(LogJsonKeys.SERVICE, "orders");
        appender = newAppender();
    }

    @AfterEach
    void tearDown() {
        LogFieldProviderRegistry.clear(owner);
        SensitiveDataConverter.clearCustomPatterns();
        SensitiveDataConverter.reloadConfig();
        appender.stop();
        context.stop();
    }

    @Test
    void capturesOnceOnProducerThreadAcrossMultipleOutlets() {
        AtomicInteger calls = new AtomicInteger();
        Thread producer = Thread.currentThread();
        register(
                "tagger",
                view -> {
                    assertSame(producer, Thread.currentThread());
                    calls.incrementAndGet();
                    return Map.of("orderId", "o-1", "items", List.of(1, 2));
                });
        LoggingEvent event = event("business");
        appender.doAppend(event);
        appender.doAppend(event);
        newAppender().doAppend(event);

        assertEquals(1, calls.get());
        assertEquals("o-1", FieldSnapshot.find(event).attributes().get("orderId"));
        assertEquals(List.of(1, 2), FieldSnapshot.find(event).attributes().get("items"));
        assertTrue(FieldSnapshot.find(event).extended());
    }

    @Test
    void capturesFreshFieldsAndRulesWhenLoggingEventBuilderIsReused() {
        LogbackMDCAdapter mdc = new LogbackMDCAdapter();
        context.setMDCAdapter(mdc);
        Logger logger = context.getLogger("reused-builder");
        logger.setLevel(Level.INFO);
        logger.setAdditive(false);
        logger.addAppender(appender);
        ListAppender<ILoggingEvent> output = new ListAppender<>();
        output.setContext(context);
        output.start();
        logger.addAppender(output);
        List<List<KeyValuePair>> originalPairs = new ArrayList<>();
        appender.addFilter(
                new Filter<>() {
                    @Override
                    public FilterReply decide(ILoggingEvent event) {
                        originalPairs.add(event.getKeyValuePairs());
                        return FilterReply.NEUTRAL;
                    }
                });
        AtomicInteger calls = new AtomicInteger();
        register(
                "tagger",
                view -> {
                    calls.incrementAndGet();
                    return Map.of("provided", view.context().get("tenant"));
                });
        Map<String, Object> payload = new LinkedHashMap<>(Map.of("n", 1));
        LoggingEventBuilder builder = logger.atInfo().addKeyValue("payload", payload);
        mdc.put("traceId", "trace-first");
        mdc.put("tenant", "first");
        SensitiveDataConverter.publishConfiguration(
                List.of(), List.of("shared-secret"), "masked-first");

        builder.log("first");
        int callsAfterFirst = calls.get();
        payload.put("n", 2);
        builder.addKeyValue("secondOnly", true);
        mdc.put("traceId", "trace-second");
        mdc.put("tenant", "second");
        SensitiveDataConverter.publishConfiguration(
                List.of(), List.of("shared-secret"), "masked-second");
        builder.log("second");

        assertEquals(2, output.list.size());
        ILoggingEvent first = output.list.get(0);
        ILoggingEvent second = output.list.get(1);
        FieldSnapshot firstSnapshot = FieldSnapshot.find(first);
        FieldSnapshot secondSnapshot = FieldSnapshot.find(second);
        assertNotNull(firstSnapshot);
        assertNotNull(secondSnapshot);
        assertSame(originalPairs.get(0), originalPairs.get(1));
        assertAll(
                () -> assertEquals("first", first.getFormattedMessage()),
                () -> assertEquals("second", second.getFormattedMessage()),
                () -> assertEquals(1, callsAfterFirst),
                () -> assertEquals(2, calls.get()),
                () -> assertNotSame(firstSnapshot, secondSnapshot),
                () -> assertEquals("trace-first", firstSnapshot.mdc().get("traceId")),
                () -> assertEquals("trace-second", secondSnapshot.mdc().get("traceId")),
                () ->
                        assertEquals(
                                Map.of("payload", Map.of("n", 1), "provided", "first"),
                                firstSnapshot.attributes()),
                () ->
                        assertEquals(
                                Map.of(
                                        "payload",
                                        Map.of("n", 2),
                                        "secondOnly",
                                        true,
                                        "provided",
                                        "second"),
                                secondSnapshot.attributes()),
                () ->
                        assertEquals(
                                "masked-first",
                                firstSnapshot.maskRules().maskValue("shared-secret")),
                () ->
                        assertEquals(
                                "masked-second",
                                secondSnapshot.maskRules().maskValue("shared-secret")),
                () ->
                        assertFalse(
                                originalPairs.get(0).stream()
                                        .anyMatch(
                                                pair -> LogJsonKeys.RESERVED_KEY.equals(pair.key))),
                () ->
                        assertFalse(
                                first.getKeyValuePairs().stream()
                                        .anyMatch(pair -> "secondOnly".equals(pair.key))));
    }

    @Test
    void freezesOriginalAndReturnedContainersAndMdcBeforeAsyncOutput() {
        Map<String, Object> eventValue = new LinkedHashMap<>(Map.of("n", 1));
        List<Object> providerValue = new ArrayList<>(List.of(3));
        Map<String, String> mdc = new LinkedHashMap<>(Map.of("tenant", "before"));
        register("tagger", view -> Map.of("provided", providerValue));
        LoggingEvent event = event("business", mdc);
        event.addKeyValuePair(new KeyValuePair("original", eventValue));
        appender.doAppend(event);
        eventValue.put("n", 2);
        providerValue.add(4);
        mdc.put("tenant", "after");

        FieldSnapshot snapshot = FieldSnapshot.find(event);
        assertEquals(Map.of("n", 1), snapshot.attributes().get("original"));
        assertEquals(List.of(3), snapshot.attributes().get("provided"));
        assertEquals("before", snapshot.mdc().get("tenant"));
    }

    @Test
    void sharesOneReadOnlyOriginalViewAndIsolatesAttemptedWrites() {
        AtomicReference<LogEventView> firstView = new AtomicReference<>();
        LogFieldProviderRegistry.register(
                owner,
                List.of(
                        new NamedProvider(
                                "write",
                                view -> {
                                    firstView.set(view);
                                    view.context().put("tenant", "changed");
                                    return Map.of();
                                }),
                        new NamedProvider(
                                "deepWrite",
                                view -> {
                                    ((Map<?, ?>) view.attributes().get("nested")).clear();
                                    return Map.of();
                                }),
                        new NamedProvider("add", view -> Map.of("added", 1)),
                        new NamedProvider(
                                "read",
                                view -> {
                                    assertSame(firstView.get(), view);
                                    assertEquals("raw", view.context().get("tenant"));
                                    assertFalse(view.attributes().containsKey("added"));
                                    assertEquals(Map.of("k", "v"), view.attributes().get("nested"));
                                    assertEquals("orders", view.service());
                                    assertEquals("hello world", view.message().orElseThrow());
                                    assertEquals("trace", view.traceId().orElseThrow());
                                    assertEquals(
                                            Instant.parse("2026-11-15T00:00:00Z"),
                                            view.timestamp());
                                    assertEquals(
                                            IllegalStateException.class.getName(),
                                            view.exception().orElseThrow().type());
                                    assertEquals(
                                            "raw exception",
                                            view.exception().orElseThrow().message().orElseThrow());
                                    return Map.of("ok", true);
                                })));
        LoggingEvent event =
                new LoggingEvent(
                        getClass().getName(),
                        context.getLogger("business"),
                        Level.INFO,
                        "hello {}",
                        new IllegalStateException("raw exception"),
                        new Object[] {"world"});
        event.setInstant(Instant.parse("2026-11-15T00:00:00Z"));
        event.setMDCPropertyMap(Map.of("tenant", "raw", "traceId", "trace"));
        event.addKeyValuePair(new KeyValuePair("nested", Map.of("k", "v")));
        appender.doAppend(event);

        FieldSnapshot snapshot = FieldSnapshot.find(event);
        assertEquals(true, snapshot.attributes().get("ok"));
        assertEquals(
                List.of(
                        new ProcessingError(null, "write", "provider_error"),
                        new ProcessingError(null, "deepWrite", "provider_error")),
                snapshot.errors());
    }

    @Test
    void honorsPriorityProtectsFrameworkFieldsAndKeepsOriginalView() {
        LogFieldProviderRegistry.register(
                owner,
                List.of(
                        new NamedProvider(
                                "first",
                                view -> Map.of("k", "a", "level", "fake", "method", "PUT")),
                        new NamedProvider("second", view -> Map.of("k", "b", "x", 1))));
        LoggingEvent event = event("access.log");
        event.addKeyValuePair(new KeyValuePair("k", "e"));
        event.addKeyValuePair(new KeyValuePair("method", "GET"));
        appender.doAppend(event);
        FieldSnapshot snapshot = FieldSnapshot.find(event);
        assertEquals(Map.of("k", "a", "method", "GET", "x", 1), snapshot.attributes());
        assertEquals(2, snapshot.errors().size());
        assertTrue(
                snapshot.errors()
                        .contains(
                                new ProcessingError(
                                        "/attributes/level", "first", "protected_field")));
        assertTrue(
                snapshot.errors()
                        .contains(
                                new ProcessingError(
                                        "/attributes/method", "first", "protected_field")));
        LoggingEvent ordinary = event("business");
        appender.doAppend(ordinary);
        assertEquals("PUT", FieldSnapshot.find(ordinary).attributes().get("method"));
    }

    @Test
    void isolatesProviderInvocationAndTraversalErrorsWithoutLeakingPartialResults() {
        AtomicInteger calls = new AtomicInteger();
        Map<String, Object> invalid = new LinkedHashMap<>();
        invalid.put("bean", new Object());
        invalid.put("nan", Double.NaN);
        invalid.put("ok", 1);
        LogFieldProviderRegistry.register(
                owner,
                List.of(
                        new NamedProvider(
                                "throws",
                                view -> {
                                    calls.incrementAndGet();
                                    throw new IllegalStateException("secret exception");
                                }),
                        new NamedProvider("lazy", view -> brokenMap()),
                        new NamedProvider(
                                "linkage",
                                view -> {
                                    throw new NoClassDefFoundError("secret class");
                                }),
                        new NamedProvider("values", view -> invalid)));
        LoggingEvent event = event("business");
        appender.doAppend(event);
        FieldSnapshot snapshot = FieldSnapshot.find(event);
        assertEquals(Map.of("ok", 1), snapshot.attributes());
        assertEquals(
                List.of(
                        new ProcessingError(null, "throws", "provider_error"),
                        new ProcessingError(null, "lazy", "provider_error"),
                        new ProcessingError(null, "linkage", "provider_error"),
                        new ProcessingError("/attributes/bean", "values", "unsupported_type"),
                        new ProcessingError("/attributes/nan", "values", "invalid_value")),
                snapshot.errors());
        appender.doAppend(event("business"));
        assertEquals(2, calls.get());
    }

    @Test
    void acceptsEmptyProvidersAndReportsInvalidProviderKeysWithoutFieldPath() {
        Map<String, Object> invalid = new LinkedHashMap<>();
        invalid.put(null, "hidden");
        invalid.put("ok", "y");
        LogFieldProviderRegistry.register(
                owner,
                List.of(
                        new NamedProvider("empty", view -> Map.of()),
                        new NamedProvider("nulls", view -> null),
                        new NamedProvider("badkey", view -> invalid)));
        LoggingEvent event = event("business");
        appender.doAppend(event);
        assertEquals(Map.of("ok", "y"), FieldSnapshot.find(event).attributes());
        assertEquals(
                List.of(new ProcessingError(null, "badkey", "invalid_value")),
                FieldSnapshot.find(event).errors());
    }

    @Test
    void skipsNestedProviderExecutionAndRestoresGuardForNextEvent() {
        LogFieldCaptureAppender nestedAppender = newAppender();
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<LoggingEvent> nested = new AtomicReference<>();
        register(
                "recursive",
                view -> {
                    calls.incrementAndGet();
                    LoggingEvent inner = event("nested");
                    nestedAppender.doAppend(inner);
                    nested.set(inner);
                    return Map.of("outer", true);
                });
        appender.doAppend(event("business"));
        assertFalse(FieldSnapshot.find(nested.get()).extended());
        assertTrue(FieldSnapshot.find(nested.get()).attributes().isEmpty());
        assertTrue(FieldSnapshot.find(nested.get()).errors().isEmpty());
        appender.doAppend(event("business"));
        assertEquals(2, calls.get());
    }

    @Test
    void capturesNestedLogFromSameLoggerBeforeItsMutableValuesAndRulesChange() {
        context.setMDCAdapter(new LogbackMDCAdapter());
        Logger logger = context.getLogger("same-logger");
        logger.setAdditive(false);
        logger.addAppender(appender);
        ListAppender<ILoggingEvent> output = new ListAppender<>();
        output.setContext(context);
        output.start();
        logger.addAppender(output);
        AtomicInteger calls = new AtomicInteger();
        Map<String, Object> mutable = new LinkedHashMap<>(Map.of("n", 1));
        SensitiveDataConverter.publishConfiguration(List.of(), List.of("nested-secret"), "masked");
        register(
                "recursive",
                view -> {
                    calls.incrementAndGet();
                    logger.atInfo().addKeyValue("nested", mutable).log("inner");
                    mutable.put("n", 2);
                    SensitiveDataConverter.publishConfiguration(List.of(), List.of(), "changed");
                    return Map.of("outer", true);
                });

        logger.info("outer");

        assertEquals(1, calls.get());
        assertEquals(2, output.list.size());
        ILoggingEvent nested = output.list.get(0);
        assertEquals("inner", nested.getFormattedMessage());
        FieldSnapshot snapshot = FieldSnapshot.find(nested);
        assertNotNull(snapshot, "同一采集出口的重入事件也必须固定基础字段");
        assertFalse(snapshot.extended());
        assertTrue(snapshot.errors().isEmpty());
        assertEquals(Map.of("nested", Map.of("n", 1)), snapshot.attributes());
        assertEquals("masked", snapshot.maskRules().maskValue("nested-secret"));
        assertEquals(true, FieldSnapshot.find(output.list.get(1)).attributes().get("outer"));
        logger.info("next");
        assertEquals(2, calls.get());
    }

    @Test
    void nestedCaptureRespectsDenyFilter() {
        AtomicReference<LoggingEvent> nested = new AtomicReference<>();
        AtomicInteger filterCalls = new AtomicInteger();
        appender.addFilter(
                new Filter<>() {
                    @Override
                    public FilterReply decide(ILoggingEvent event) {
                        filterCalls.incrementAndGet();
                        return "nested".equals(event.getLoggerName())
                                ? FilterReply.DENY
                                : FilterReply.NEUTRAL;
                    }
                });
        register(
                "recursive",
                view -> {
                    LoggingEvent inner = event("nested");
                    appender.doAppend(inner);
                    nested.set(inner);
                    return Map.of();
                });

        appender.doAppend(event("business"));

        assertEquals(2, filterCalls.get());
        assertNull(FieldSnapshot.find(nested.get()));
    }

    @Test
    void stoppedCaptureDoesNotCaptureNestedEventOrInvokeItsFilters() {
        AtomicReference<LoggingEvent> nested = new AtomicReference<>();
        AtomicInteger filterCalls = new AtomicInteger();
        appender.addFilter(
                new Filter<>() {
                    @Override
                    public FilterReply decide(ILoggingEvent event) {
                        filterCalls.incrementAndGet();
                        return FilterReply.NEUTRAL;
                    }
                });
        register(
                "recursive",
                view -> {
                    appender.stop();
                    LoggingEvent inner = event("nested");
                    appender.doAppend(inner);
                    nested.set(inner);
                    return Map.of();
                });

        appender.doAppend(event("business"));

        assertEquals(1, filterCalls.get());
        assertNull(FieldSnapshot.find(nested.get()));
    }

    @Test
    void nestedFilterFailureIsRecordedAsAppenderStatusAndDoesNotEscape() {
        AtomicReference<LoggingEvent> nested = new AtomicReference<>();
        appender.addFilter(
                new Filter<>() {
                    @Override
                    public FilterReply decide(ILoggingEvent event) {
                        if ("nested".equals(event.getLoggerName())) {
                            throw new IllegalStateException("filter failure");
                        }
                        return FilterReply.NEUTRAL;
                    }
                });
        register(
                "recursive",
                view -> {
                    LoggingEvent inner = event("nested");
                    assertDoesNotThrow(() -> appender.doAppend(inner));
                    nested.set(inner);
                    return Map.of("outer", true);
                });

        LoggingEvent outer = event("business");
        appender.doAppend(outer);

        assertNull(FieldSnapshot.find(nested.get()));
        assertEquals(true, FieldSnapshot.find(outer).attributes().get("outer"));
        assertTrue(
                context.getStatusManager().getCopyOfStatusList().stream()
                        .anyMatch(status -> status.getMessage().contains("failed to append")));
    }

    @Test
    void leavesFatalErrorsToCallerAndStillRestoresProviderGuard() {
        register(
                "fatal",
                view -> {
                    throw new OutOfMemoryError("fatal");
                });
        assertThrows(OutOfMemoryError.class, () -> appender.doAppend(event("business")));
        register("healthy", view -> Map.of("healthy", true));
        LoggingEvent event = event("business");
        appender.doAppend(event);
        assertEquals(true, FieldSnapshot.find(event).attributes().get("healthy"));
    }

    @Test
    void capturesBaseBeforeRegistrationAndAfterOwnerCloses() {
        LoggingEvent before = event("business");
        appender.doAppend(before);
        assertFalse(FieldSnapshot.find(before).extended());
        AtomicInteger calls = new AtomicInteger();
        register(
                "tagger",
                view -> {
                    calls.incrementAndGet();
                    return Map.of("live", true);
                });
        LoggingEvent live = event("business");
        appender.doAppend(live);
        LogFieldProviderRegistry.clear(new Object());
        assertEquals(1, LogFieldProviderRegistry.current().size());
        LogFieldProviderRegistry.clear(owner);
        LoggingEvent after = event("business");
        appender.doAppend(after);
        assertFalse(FieldSnapshot.find(after).extended());
        assertTrue(FieldSnapshot.find(after).errors().isEmpty());
        assertEquals(1, calls.get());
    }

    @Test
    void skipsDisabledCaptureAndExistingReservedKey() {
        AtomicInteger calls = new AtomicInteger();
        register(
                "tagger",
                view -> {
                    calls.incrementAndGet();
                    return Map.of();
                });
        appender.setActive(false);
        LoggingEvent disabled = event("business");
        appender.doAppend(disabled);
        assertNull(FieldSnapshot.find(disabled));
        appender.setActive(true);
        LoggingEvent existing = event("business");
        existing.addKeyValuePair(new KeyValuePair(LogJsonKeys.RESERVED_KEY, "external"));
        appender.doAppend(existing);
        assertNull(FieldSnapshot.find(existing));
        assertEquals(0, calls.get());
    }

    @Test
    void fixesRulesAtCaptureAndDoesNotExposeSnapshotToOtherEncoders() {
        SensitiveDataConverter.publishConfiguration(List.of(), List.of("capture-secret"), "masked");
        LoggingEvent event = event("business");
        appender.doAppend(event);
        FieldSnapshot snapshot = FieldSnapshot.find(event);
        SensitiveDataConverter.publishConfiguration(List.of(), List.of(), "changed");
        assertEquals("masked", snapshot.maskRules().maskValue("capture-secret"));
        assertNotSame(
                snapshot.maskRules(), FieldSnapshot.captureBase(event("business")).maskRules());
        assertEquals("[mimir-fields]", event.getKeyValuePairs().get(0).value);
        PatternLayout layout = new PatternLayout();
        layout.setContext(context);
        layout.setPattern("%kvp");
        layout.start();
        assertTrue(layout.doLayout(event).contains("[mimir-fields]"));
        JsonEncoder encoder = new JsonEncoder();
        encoder.setContext(context);
        encoder.start();
        String json = new String(encoder.encode(event), StandardCharsets.UTF_8);
        assertTrue(json.contains("[mimir-fields]"));
        assertFalse(json.contains("capture-secret"));
    }

    private void register(String name, LogFieldProvider provider) {
        LogFieldProviderRegistry.register(owner, List.of(new NamedProvider(name, provider)));
    }

    private LogFieldCaptureAppender newAppender() {
        LogFieldCaptureAppender capture = new LogFieldCaptureAppender();
        capture.setContext(context);
        capture.start();
        return capture;
    }

    private LoggingEvent event(String logger) {
        return event(logger, Map.of());
    }

    private LoggingEvent event(String logger, Map<String, String> mdc) {
        LoggingEvent event =
                new LoggingEvent(
                        getClass().getName(),
                        context.getLogger(logger),
                        Level.INFO,
                        "hello",
                        null,
                        null);
        event.setMDCPropertyMap(mdc);
        return event;
    }

    private static Map<String, Object> brokenMap() {
        return new AbstractMap<>() {
            @Override
            public boolean isEmpty() {
                return false;
            }

            @Override
            public Set<Entry<String, Object>> entrySet() {
                throw new IllegalStateException("secret traversal");
            }
        };
    }
}
