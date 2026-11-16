package com.yggdrasil.labs.log.json;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 将允许的字段值复制为不可变 JSON 值，不反射遍历或字符串化未知对象。 */
final class ValueNormalizer {

    private static final int MAX_DEPTH = 32;
    private static final Result REJECTED = new Result(false, null);

    private ValueNormalizer() {}

    static Result normalize(
            Object value, JsonPointer path, List<ProcessingError> errors, String provider) {
        return normalize(value, path, 1, new IdentityHashMap<>(), errors, provider);
    }

    private static Result normalize(
            Object value,
            JsonPointer path,
            int depth,
            IdentityHashMap<Object, Boolean> seen,
            List<ProcessingError> errors,
            String provider) {
        try {
            if (depth > MAX_DEPTH) {
                return reject(path, errors, provider, "invalid_value");
            }
            if (value == null || value instanceof String || value instanceof Boolean) {
                return new Result(true, value);
            }
            if (value instanceof Number) {
                return normalizeNumber(value, path, errors, provider);
            }
            if (!(value instanceof Map<?, ?>) && !(value instanceof List<?>)) {
                return reject(path, errors, provider, "unsupported_type");
            }
            if (seen.containsKey(value)) {
                return reject(path, errors, provider, "circular_reference");
            }
            seen.put(value, Boolean.TRUE);
            try {
                if (value instanceof Map<?, ?> map) {
                    return normalizeMap(map, path, depth, seen, errors, provider);
                }
                return normalizeList((List<?>) value, path, depth, seen, errors, provider);
            } finally {
                seen.remove(value);
            }
        } catch (Exception | LinkageError ex) {
            return reject(path, errors, provider, "field_error");
        }
    }

    private static Result normalizeNumber(
            Object value, JsonPointer path, List<ProcessingError> errors, String provider) {
        if (value instanceof Integer
                || value instanceof Long
                || value instanceof Short
                || value instanceof Byte
                || value.getClass().equals(BigInteger.class)
                || value.getClass().equals(BigDecimal.class)) {
            return new Result(true, value);
        }
        if (value instanceof Float number) {
            return Float.isFinite(number)
                    ? new Result(true, value)
                    : reject(path, errors, provider, "invalid_value");
        }
        if (value instanceof Double number) {
            return Double.isFinite(number)
                    ? new Result(true, value)
                    : reject(path, errors, provider, "invalid_value");
        }
        return reject(path, errors, provider, "unsupported_type");
    }

    private static Result normalizeMap(
            Map<?, ?> map,
            JsonPointer path,
            int depth,
            IdentityHashMap<Object, Boolean> seen,
            List<ProcessingError> errors,
            String provider) {
        List<Map.Entry<String, Object>> entries = new ArrayList<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                return reject(path, errors, provider, "invalid_value");
            }
            entries.add(new SimpleImmutableEntry<>(key, entry.getValue()));
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : entries) {
            Result member =
                    normalize(
                            entry.getValue(),
                            path.append(entry.getKey()),
                            depth + 1,
                            seen,
                            errors,
                            provider);
            if (member.accepted()) {
                normalized.put(entry.getKey(), member.value());
            }
        }
        return new Result(true, Collections.unmodifiableMap(normalized));
    }

    private static Result normalizeList(
            List<?> list,
            JsonPointer path,
            int depth,
            IdentityHashMap<Object, Boolean> seen,
            List<ProcessingError> errors,
            String provider) {
        List<?> entries = new ArrayList<>(list);
        List<Object> normalized = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            Result member =
                    normalize(
                            entries.get(i),
                            path.append(Integer.toString(i)),
                            depth + 1,
                            seen,
                            errors,
                            provider);
            if (member.accepted()) {
                normalized.add(member.value());
            }
        }
        return new Result(true, Collections.unmodifiableList(normalized));
    }

    private static Result reject(
            JsonPointer path, List<ProcessingError> errors, String provider, String reason) {
        errors.add(new ProcessingError(path.toString(), provider, reason));
        return REJECTED;
    }

    /** accepted 区分合法 null 与失败字段。 */
    record Result(boolean accepted, Object value) {}
}
