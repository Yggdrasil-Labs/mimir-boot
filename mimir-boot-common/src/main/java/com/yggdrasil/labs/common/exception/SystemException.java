package com.yggdrasil.labs.common.exception;

import lombok.Getter;

import java.io.Serial;

/**
 * 系统异常抽象类
 * <p>
 * 用于表示系统层面的异常，通常是不可预期的、系统级的异常。
 * 例如：系统错误、系统繁忙、系统超时、系统不可用等。
 * </p>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@Getter
public class SystemException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 构造方法
     *
     * @param code    错误码
     * @param message 错误信息
     */
    public SystemException(String code, String message) { super(code, message); }

    /**
     * 构造方法
     *
     * @param code    错误码
     * @param message 错误信息
     * @param cause   原因
     */
    public SystemException(String code, String message, Throwable cause) { super(code, message, cause); }

    /**
     * 构造方法
     *
     * @param errorCode 错误码枚举
     */
    public SystemException(ErrorCode errorCode) { super(errorCode); }

    /**
     * 构造方法
     *
     * @param errorCode 错误码枚举
     * @param cause     原因
     */
    public SystemException(ErrorCode errorCode, Throwable cause) { super(errorCode, cause); }
}

