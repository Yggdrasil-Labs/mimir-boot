package com.yggdrasil.labs.common.exception;

import lombok.Getter;

import java.io.Serial;
import java.util.List;

/**
 * 校验异常类
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@Getter
public class ValidationException extends BusinessException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 校验错误详情
     */
    private final List<ValidationError> errors;

    /**
     * 构造方法
     *
     * @param message 错误信息
     * @param errors  校验错误详情
     */
    public ValidationException(String message, List<ValidationError> errors) {
        super(ErrorCode.PARAM_INVALID.getCode(), message);
        this.errors = errors;
    }

    /**
     * 构造方法
     *
     * @param errors 校验错误详情
     */
    public ValidationException(List<ValidationError> errors) {
        super(ErrorCode.PARAM_INVALID.getCode(), "参数校验失败");
        this.errors = errors;
    }

    /**
     * 校验错误详情
     *
     * @param field   字段名
     * @param message 错误信息
     * @param value   错误值
     */
    public record ValidationError(String field, String message, Object value) {

        /**
         * 构造方法
         *
         * @param field   字段名
         * @param message 错误信息
         * @param value   错误值
         */
        public ValidationError {
        }

        /**
         * 构造方法
         *
         * @param field   字段名
         * @param message 错误信息
         */
        public ValidationError(String field, String message) {
            this(field, message, null);
        }
    }
}
