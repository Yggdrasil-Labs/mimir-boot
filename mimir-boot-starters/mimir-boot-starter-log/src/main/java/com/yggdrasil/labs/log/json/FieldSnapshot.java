package com.yggdrasil.labs.log.json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.event.KeyValuePair;

import com.yggdrasil.labs.log.converter.StructuredMaskSupport;
import com.yggdrasil.labs.log.converter.StructuredMaskSupport.MaskRules;

import ch.qos.logback.classic.spi.ILoggingEvent;

/** 采集阶段固定的字段及规则；不经公开键值对的 value 暴露。 */
record FieldSnapshot(
        Map<String, String> mdc,
        Map<String, Object> attributes,
        List<ProcessingError> errors,
        MaskRules maskRules,
        boolean extended) {

    FieldSnapshot {
        mdc = Collections.unmodifiableMap(new LinkedHashMap<>(mdc));
        attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
        errors = List.copyOf(errors);
    }

    static FieldSnapshot find(ILoggingEvent event) {
        List<KeyValuePair> pairs = event.getKeyValuePairs();
        if (pairs != null) {
            for (KeyValuePair pair : pairs) {
                if (pair instanceof FieldSnapshotKeyValuePair captured) {
                    return captured.snapshot;
                }
            }
        }
        return null;
    }

    /** 未配置捕获出口时使用；不调用扩展。 */
    static FieldSnapshot captureBase(ILoggingEvent event) {
        MaskRules rules = StructuredMaskSupport.currentRules();
        List<ProcessingError> errors = new ArrayList<>();
        Map<String, String> mdc = captureMdc(event, errors);
        Map<String, Object> attributes = new LinkedHashMap<>();
        List<KeyValuePair> pairs = event.getKeyValuePairs();
        if (pairs != null) {
            for (KeyValuePair pair : new ArrayList<>(pairs)) {
                if (pair == null || pair.key == null) {
                    errors.add(new ProcessingError(null, null, "invalid_value"));
                    continue;
                }
                if (LogJsonKeys.RESERVED_KEY.equals(pair.key)) {
                    continue;
                }
                attributes.remove(pair.key);
                ValueNormalizer.Result normalized =
                        ValueNormalizer.normalize(
                                pair.value,
                                JsonPointer.parse("/attributes").append(pair.key),
                                errors,
                                null);
                if (normalized.accepted()) {
                    attributes.put(pair.key, normalized.value());
                }
            }
        }
        return new FieldSnapshot(mdc, attributes, errors, rules, false);
    }

    private static Map<String, String> captureMdc(
            ILoggingEvent event, List<ProcessingError> errors) {
        try {
            Map<String, String> mdc = event.getMDCPropertyMap();
            return mdc == null ? Map.of() : new LinkedHashMap<>(mdc);
        } catch (Exception | LinkageError ex) {
            errors.add(new ProcessingError("/context", null, "field_error"));
            return Map.of();
        }
    }
}
