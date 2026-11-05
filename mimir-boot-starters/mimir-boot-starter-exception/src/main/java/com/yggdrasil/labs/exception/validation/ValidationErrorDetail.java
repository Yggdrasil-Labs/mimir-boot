package com.yggdrasil.labs.exception.validation;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/** 只含安全声明元数据的单项校验明细。 */
public record ValidationErrorDetail(
        ValidationErrorSource source,
        String path,
        Integer parameterIndex,
        String constraint,
        String message)
        implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    public ValidationErrorDetail {
        Objects.requireNonNull(source, "source");
        path = normalize(path, 256);
        constraint = normalize(constraint, Integer.MAX_VALUE);
        if (!constraint.matches("[A-Za-z][A-Za-z0-9_.-]{0,63}")) {
            constraint = "Validation";
        }
        message = normalize(message, 256);
        if (parameterIndex != null && parameterIndex < 0) {
            throw new IllegalArgumentException("参数序号不能为负数");
        }
    }

    private static String normalize(String value, int limit) {
        Objects.requireNonNull(value, "校验明细字符串");
        StringBuilder result = new StringBuilder();
        value.codePoints()
                .filter(codePoint -> !Character.isISOControl(codePoint))
                .limit(limit)
                .forEach(result::appendCodePoint);
        return result.toString();
    }
}
