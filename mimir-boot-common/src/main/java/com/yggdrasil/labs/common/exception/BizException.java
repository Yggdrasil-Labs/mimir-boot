package com.yggdrasil.labs.common.exception;

import java.io.Serial;

import lombok.Getter;

/**
 * 业务异常类
 *
 * <p>用于表示业务层面的异常，通常是可预期的、可处理的异常。 例如：数据不存在、数据已存在、操作不允许等。
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@Getter
public class BizException extends BaseException {

    @Serial private static final long serialVersionUID = 1L;

    /**
     * 构造方法
     *
     * @param code 错误码
     * @param message 错误信息
     */
    public BizException(String code, String message) {
        super(code, message);
    }

    /**
     * 构造方法
     *
     * @param code 错误码
     * @param message 错误信息
     * @param cause 原因
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
     * @param cause 原因
     */
    public BizException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    /**
     * 按错误定义创建异常，使用默认消息
     *
     * @param error 错误定义，code 不能为空白或成功码
     * @return 新异常，不自动抛出
     * @since 2.4.0
     */
    public static BizException fromError(ErrorDefinition error) {
        return fromError(error, null, null);
    }

    /**
     * 按错误定义创建异常
     *
     * @param error 错误定义，code 不能为空白或成功码
     * @param message 覆盖消息，为 null 时使用默认消息
     * @return 新异常，不自动抛出
     * @since 2.4.0
     */
    public static BizException fromError(ErrorDefinition error, String message) {
        return fromError(error, message, null);
    }

    /**
     * 按错误定义创建异常
     *
     * @param error 错误定义，code 不能为空白或成功码
     * @param message 覆盖消息，为 null 时使用默认消息
     * @param cause 原因，允许为 null
     * @return 新异常，不自动抛出
     * @since 2.4.0
     */
    public static BizException fromError(ErrorDefinition error, String message, Throwable cause) {
        String code = ErrorDefinitions.requireCode(error);
        return new BizException(code, ErrorDefinitions.resolveMessage(error, message), cause);
    }
}
