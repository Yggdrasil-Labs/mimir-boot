package com.yggdrasil.labs.common.exception;

import lombok.Getter;

import java.io.Serial;

/**
 * 业务异常类
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@Getter
public class BusinessException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 错误码
     */
    private final String code;

    /**
     * 错误信息
     */
    private final String message;

    /**
     * 构造方法
     *
     * @param code    错误码
     * @param message 错误信息
     */
    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    /**
     * 构造方法
     *
     * @param code    错误码
     * @param message 错误信息
     * @param cause   原因
     */
    public BusinessException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.message = message;
    }

    /**
     * 构造方法
     *
     * @param errorCode 错误码枚举
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
    }

    /**
     * 构造方法
     *
     * @param errorCode 错误码枚举
     * @param cause     原因
     */
    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
    }
}
