package com.yggdrasil.labs.exception.validation;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/** 单次输入校验的明细快照及截断信息。 */
public record ValidationErrorData(
        List<ValidationErrorDetail> errors, int totalCount, boolean truncated)
        implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    public ValidationErrorData {
        errors = List.copyOf(errors);
        if (totalCount < errors.size() || truncated != (totalCount > errors.size())) {
            throw new IllegalArgumentException("校验错误总数或截断标记不一致");
        }
    }
}
