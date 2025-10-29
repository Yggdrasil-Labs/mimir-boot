package com.yggdrasil.labs.common.exception;

import lombok.Getter;

import java.io.Serial;

/**
 * 抽象基础异常，封装通用错误码与消息
 */
@Getter
public abstract class BaseException extends RuntimeException implements IException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String code;
    private final String message;

    protected BaseException(String code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    protected BaseException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.message = message;
    }

    protected BaseException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
    }

    protected BaseException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
    }
}


