package com.yggdrasil.labs.log.json;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import ch.qos.logback.core.Context;

/** 编码器与捕获出口共用的不可变启动设置（内部实现，非公共 API）。 */
public record LogJsonSettings(
        String service,
        String environment,
        String instance,
        Set<String> excludeMdcKeys,
        Map<JsonPointer, Integer> limits) {

    public LogJsonSettings {
        excludeMdcKeys = Collections.unmodifiableSet(new LinkedHashSet<>(excludeMdcKeys));
        limits = Collections.unmodifiableMap(new LinkedHashMap<>(limits));
    }

    /** 一次性读取 Context 属性；service 保持原值，其余设置解码为不可变副本。 */
    public static LogJsonSettings from(Context context) {
        String service = context.getProperty(LogJsonKeys.SERVICE);
        Map<JsonPointer, Integer> limits = new LinkedHashMap<>();
        LogJsonSettingsCodec.decodeLimits(context.getProperty(LogJsonKeys.FIELD_LENGTH_LIMITS))
                .forEach((pointer, limit) -> limits.put(JsonPointer.parse(pointer), limit));
        return new LogJsonSettings(
                service == null || service.isBlank() ? "application" : service,
                optional(context.getProperty(LogJsonKeys.ENVIRONMENT)),
                optional(context.getProperty(LogJsonKeys.INSTANCE)),
                new LinkedHashSet<>(
                        LogJsonSettingsCodec.decodeList(
                                context.getProperty(LogJsonKeys.EXCLUDE_MDC_KEYS))),
                limits);
    }

    private static String optional(String value) {
        return value == null || value.isEmpty() ? null : LogJsonSettingsCodec.decode(value);
    }
}
