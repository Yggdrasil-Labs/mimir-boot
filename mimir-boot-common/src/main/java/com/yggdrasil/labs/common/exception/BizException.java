package com.yggdrasil.labs.common.exception;

import lombok.Getter;

import java.io.Serial;

/**
 * 业务异常类
 * <p>
 * 用于表示业务层面的异常，通常是可预期的、可处理的异常。
 * 例如：数据不存在、数据已存在、操作不允许等。
 * </p>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@Getter
public class BizException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 构造方法
     *
     * @param code    错误码
     * @param message 错误信息
     */
    public BizException(String code, String message) {
        super(code, message);
    }

    /**
     * 构造方法
     *
     * @param code    错误码
     * @param message 错误信息
     * @param cause   原因
     */
    public BizException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }

    /**
     * 构造方法
     *
     * @param errorCode 错误码枚举
     */
    public BizException(ErrorCode errorCode) {
        super(errorCode);
    }

    /**
     * 构造方法
     *
     * @param errorCode 错误码枚举
     * @param cause     原因
     */
    public BizException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
