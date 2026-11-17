package com.yggdrasil.labs.log.json;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonGenerator;
import com.yggdrasil.labs.log.converter.StructuredMaskSupport.MaskRules;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import net.logstash.logback.composite.AbstractJsonProvider;

/** 先在内存中治理字段，再通过 JSON 生成器写出单条事件。 */
final class GovernedFieldsJsonProvider extends AbstractJsonProvider<ILoggingEvent> {
    private static final String FIELD_ERROR = "field_error";
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
                    .withZone(ZoneId.systemDefault());
    private final LogJsonSettings settings;

    GovernedFieldsJsonProvider(LogJsonSettings settings) {
        this.settings = settings;
    }

    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent event) throws IOException {
        FieldSnapshot snapshot = FieldSnapshot.find(event);
        if (snapshot == null) {
            snapshot = FieldSnapshot.captureBase(event);
        }
        List<ProcessingError> errors = new ArrayList<>(snapshot.errors());
        Set<String> truncated = new LinkedHashSet<>();
        Map<String, Object> fields = envelope(event);
        MaskRules rules = snapshot.maskRules();
        putText(fields, "message", event.getFormattedMessage(), rules, errors);
        Map<String, Object> context = context(fields, snapshot, rules, errors);
        if (!context.isEmpty()) {
            fields.put("context", context);
        }
        Map<String, Object> attributes = attributes(snapshot, rules, errors);
        if (!attributes.isEmpty()) {
            fields.put("attributes", attributes);
        }
        if (event.getThrowableProxy() != null) {
            fields.put("exception", exception(event.getThrowableProxy(), rules, errors));
        }
        Map<String, Object> result = truncateMap(fields, "", truncated, errors);
        attachProcessing(result, truncated, errors);
        for (Map.Entry<String, Object> field : result.entrySet()) {
            generator.writeObjectField(field.getKey(), field.getValue());
        }
    }

    private static void attachProcessing(
            Map<String, Object> result, Set<String> truncated, List<ProcessingError> errors) {
        Map<String, Object> processing = new LinkedHashMap<>();
        if (!truncated.isEmpty()) {
            processing.put("truncatedFields", List.copyOf(truncated));
        }
        if (!errors.isEmpty()) {
            processing.put("errors", errorValues(errors));
        }
        if (!processing.isEmpty()) {
            result.put("processing", processing);
        }
    }

    private Map<String, Object> envelope(ILoggingEvent event) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("timestamp", TIMESTAMP.format(Instant.ofEpochMilli(event.getTimeStamp())));
        fields.put("level", event.getLevel().toString());
        fields.put("service", settings.service());
        putNonempty(fields, "environment", settings.environment());
        putNonempty(fields, "instance", settings.instance());
        fields.put("thread", event.getThreadName());
        fields.put("logger", event.getLoggerName());
        return fields;
    }

    private Map<String, Object> context(
            Map<String, Object> fields,
            FieldSnapshot snapshot,
            MaskRules rules,
            List<ProcessingError> errors) {
        Map<String, Object> context = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : snapshot.mdc().entrySet()) {
            String key = entry.getKey();
            if ("traceId".equals(key) || "spanId".equals(key)) {
                putNonempty(fields, key, entry.getValue());
            } else if (!settings.excludeMdcKeys().contains(key)) {
                putStructured(context, key, entry.getValue(), "/context", rules, errors);
            }
        }
        return context;
    }

    private static Map<String, Object> attributes(
            FieldSnapshot snapshot, MaskRules rules, List<ProcessingError> errors) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        snapshot.attributes()
                .forEach(
                        (key, value) ->
                                putStructured(
                                        attributes, key, value, "/attributes", rules, errors));
        return attributes;
    }

    private static Map<String, Object> exception(
            IThrowableProxy throwable, MaskRules rules, List<ProcessingError> errors) {
        Map<String, Object> exception = new LinkedHashMap<>();
        exception.put("type", throwable.getClassName());
        putText(exception, "message", throwable.getMessage(), rules, errors, "/exception/message");
        putText(
                exception,
                "stackTrace",
                ThrowableProxyUtil.asString(throwable),
                rules,
                errors,
                "/exception/stackTrace");
        return exception;
    }

    private static void putNonempty(Map<String, Object> fields, String key, String value) {
        if (value != null && !value.isEmpty()) {
            fields.put(key, value);
        }
    }

    private static void putText(
            Map<String, Object> fields,
            String key,
            String value,
            MaskRules rules,
            List<ProcessingError> errors) {
        putText(fields, key, value, rules, errors, "/" + key);
    }

    private static void putText(
            Map<String, Object> fields,
            String key,
            String value,
            MaskRules rules,
            List<ProcessingError> errors,
            String path) {
        if (value == null) {
            return;
        }
        try {
            fields.put(key, rules.maskText(value));
        } catch (RuntimeException e) {
            errors.add(new ProcessingError(path, null, FIELD_ERROR));
        }
    }

    private static void putStructured(
            Map<String, Object> target,
            String key,
            Object value,
            String parent,
            MaskRules rules,
            List<ProcessingError> errors) {
        String path = parent + "/" + JsonPointer.escape(key);
        try {
            target.put(
                    key,
                    rules.isSensitiveKey(key)
                            ? rules.replacement()
                            : maskValue(value, path, rules, errors));
        } catch (RuntimeException e) {
            errors.add(new ProcessingError(path, null, FIELD_ERROR));
        }
    }

    private static Object maskValue(
            Object value, String path, MaskRules rules, List<ProcessingError> errors) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach(
                    (key, child) ->
                            putStructured(result, (String) key, child, path, rules, errors));
            return result;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                try {
                    result.add(maskValue(list.get(i), path + "/" + i, rules, errors));
                } catch (RuntimeException e) {
                    errors.add(new ProcessingError(path + "/" + i, null, FIELD_ERROR));
                }
            }
            return result;
        }
        if (value == null) {
            return null;
        }
        String original = String.valueOf(value);
        String masked = rules.maskValue(original);
        return value instanceof String || !original.equals(masked) ? masked : value;
    }

    private Map<String, Object> truncateMap(
            Map<?, ?> values, String path, Set<String> truncated, List<ProcessingError> errors) {
        Map<String, Object> result = new LinkedHashMap<>();
        values.forEach(
                (key, value) -> {
                    String name = (String) key;
                    String childPath = path + "/" + JsonPointer.escape(name);
                    try {
                        result.put(name, truncate(value, childPath, truncated, errors));
                    } catch (RuntimeException e) {
                        errors.add(new ProcessingError(childPath, null, FIELD_ERROR));
                    }
                });
        return result;
    }

    private Object truncate(
            Object value, String path, Set<String> truncated, List<ProcessingError> errors) {
        if (value instanceof Map<?, ?> map) {
            return truncateMap(map, path, truncated, errors);
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                result.add(truncate(list.get(i), path + "/" + i, truncated, errors));
            }
            return result;
        }
        Integer limit = settings.limits().get(JsonPointer.parse(path));
        if (value instanceof String text
                && limit != null
                && text.codePointCount(0, text.length()) > limit) {
            truncated.add(path);
            return text.substring(0, text.offsetByCodePoints(0, limit));
        }
        return value;
    }

    private static List<Map<String, Object>> errorValues(List<ProcessingError> errors) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ProcessingError error : errors) {
            Map<String, Object> item = new LinkedHashMap<>();
            if (error.field() != null) {
                item.put("field", error.field());
            }
            if (error.provider() != null) {
                item.put("provider", error.provider());
            }
            item.put("reason", error.reason());
            result.add(item);
        }
        return result;
    }
}
