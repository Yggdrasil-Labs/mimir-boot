package com.yggdrasil.labs.log.json;

import java.time.Instant;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.event.KeyValuePair;

import com.yggdrasil.labs.log.field.LogEventView;
import com.yggdrasil.labs.log.field.LogExceptionView;
import com.yggdrasil.labs.log.json.LogFieldProviderRegistry.NamedProvider;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.spi.FilterReply;

/**
 * 在产生日志的线程复制字段并调用扩展，不输出内容。
 *
 * <p>必须位于同一 Logger 的其他出口之前，尤其必须在异步出口之前。默认启用，可经 active 属性关闭。
 */
public class LogFieldCaptureAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    private static final ThreadLocal<Boolean> IN_PROVIDER = new ThreadLocal<>();
    private static final int MAX_NESTED_ERROR_REPORTS = 3;

    private final ThreadLocal<Boolean> capturingNested = new ThreadLocal<>();
    private final AtomicInteger nestedErrorReports = new AtomicInteger();

    private boolean active = true;
    private LogJsonSettings settings;

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public void start() {
        settings = LogJsonSettings.from(getContext());
        super.start();
    }

    @Override
    public void doAppend(ILoggingEvent event) {
        // 保留过滤器等采集过程自身的重入保护，仅放行扩展产生的第一层嵌套事件。
        if (Boolean.TRUE.equals(capturingNested.get())) {
            return;
        }
        if (!Boolean.TRUE.equals(IN_PROVIDER.get()) || !isStarted()) {
            super.doAppend(event);
            return;
        }
        // 父类的 guard 会跳过同一出口的重入事件；仍须在产生线程固定其基础字段。
        capturingNested.set(Boolean.TRUE);
        try {
            if (getFilterChainDecision(event) != FilterReply.DENY) {
                append(event);
            }
        } catch (Exception ex) {
            if (nestedErrorReports.getAndIncrement() < MAX_NESTED_ERROR_REPORTS) {
                addError("Appender [" + getName() + "] failed to append.", ex);
            }
        } finally {
            capturingNested.remove();
        }
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (!active || containsSnapshotKey(event)) {
            return;
        }
        // 产生线程上的标准 Logback 事件可挂载快照；只读事件由编码器使用基础快照。
        if (!(event instanceof LoggingEvent mutable)) {
            return;
        }
        FieldSnapshot base = FieldSnapshot.captureBase(event);
        List<NamedProvider> providers = LogFieldProviderRegistry.current();
        FieldSnapshot snapshot = base;
        if (!Boolean.TRUE.equals(IN_PROVIDER.get()) && !providers.isEmpty()) {
            LogEventView view = view(event, base);
            IN_PROVIDER.set(Boolean.TRUE);
            try {
                snapshot = extend(base, view, providers);
            } finally {
                IN_PROVIDER.remove();
            }
        }
        // Logback 与 SLF4J builder 共享列表，保留键只能挂载到本事件的副本。
        List<KeyValuePair> pairs = mutable.getKeyValuePairs();
        if (pairs != null) {
            mutable.setKeyValuePairs(new ArrayList<>(pairs));
        }
        mutable.addKeyValuePair(new FieldSnapshotKeyValuePair(snapshot));
    }

    private static boolean containsSnapshotKey(ILoggingEvent event) {
        List<KeyValuePair> pairs = event.getKeyValuePairs();
        return pairs != null
                && pairs.stream()
                        .anyMatch(p -> p != null && LogJsonKeys.RESERVED_KEY.equals(p.key));
    }

    private static FieldSnapshot extend(
            FieldSnapshot base, LogEventView view, List<NamedProvider> providers) {
        Map<String, Object> attributes = new LinkedHashMap<>(base.attributes());
        List<ProcessingError> errors = new ArrayList<>(base.errors());
        Set<String> claimed = new HashSet<>();
        for (NamedProvider provider : providers) {
            List<Map.Entry<?, ?>> entries;
            try {
                Map<?, ?> raw = provider.provider().provide(view);
                if (raw == null || raw.isEmpty()) {
                    continue;
                }
                entries = new ArrayList<>();
                for (Map.Entry<?, ?> entry : raw.entrySet()) {
                    entries.add(new SimpleImmutableEntry<>(entry.getKey(), entry.getValue()));
                }
            } catch (Exception | LinkageError ex) {
                errors.add(new ProcessingError(null, provider.beanName(), "provider_error"));
                continue;
            }
            merge(entries, provider.beanName(), view.logger(), attributes, claimed, errors);
        }
        return new FieldSnapshot(base.mdc(), attributes, errors, base.maskRules(), true);
    }

    private static void merge(
            List<Map.Entry<?, ?>> entries,
            String provider,
            String logger,
            Map<String, Object> attributes,
            Set<String> claimed,
            List<ProcessingError> errors) {
        for (Map.Entry<?, ?> entry : entries) {
            if (!(entry.getKey() instanceof String key)) {
                errors.add(new ProcessingError(null, provider, "invalid_value"));
                continue;
            }
            JsonPointer path = JsonPointer.parse("/attributes").append(key);
            if (LogJsonKeys.TOP_LEVEL_FIELDS.contains(key)
                    || ("access.log".equals(logger) && LogJsonKeys.ACCESS_FIELDS.contains(key))) {
                errors.add(new ProcessingError(path.toString(), provider, "protected_field"));
                continue;
            }
            if (claimed.contains(key)) {
                continue;
            }
            attributes.remove(key);
            ValueNormalizer.Result result =
                    ValueNormalizer.normalize(entry.getValue(), path, errors, provider);
            if (result.accepted()) {
                attributes.put(key, result.value());
                claimed.add(key);
            }
        }
    }

    private LogEventView view(ILoggingEvent event, FieldSnapshot base) {
        IThrowableProxy throwable = event.getThrowableProxy();
        Optional<LogExceptionView> exception =
                throwable == null
                        ? Optional.empty()
                        : Optional.of(
                                new ExceptionView(
                                        throwable.getClassName(),
                                        Optional.ofNullable(throwable.getMessage())));
        return new EventView(
                event.getInstant(),
                event.getLevel().toString(),
                settings.service(),
                Optional.ofNullable(settings.environment()),
                Optional.ofNullable(settings.instance()),
                event.getLoggerName(),
                event.getThreadName(),
                Optional.ofNullable(event.getFormattedMessage()),
                Optional.ofNullable(base.mdc().get("traceId")),
                Optional.ofNullable(base.mdc().get("spanId")),
                base.mdc(),
                base.attributes(),
                exception);
    }

    private record EventView(
            Instant timestamp,
            String level,
            String service,
            Optional<String> environment,
            Optional<String> instance,
            String logger,
            String thread,
            Optional<String> message,
            Optional<String> traceId,
            Optional<String> spanId,
            Map<String, String> context,
            Map<String, Object> attributes,
            Optional<LogExceptionView> exception)
            implements LogEventView {}

    private record ExceptionView(String type, Optional<String> message)
            implements LogExceptionView {}
}
