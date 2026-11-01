// 兼容性 fixture：按 v2.3.0 标签 mimir-boot-common 公共签名去 Lombok 后的最小历史源码，仅用于编译旧消费者。
package com.yggdrasil.labs.common.exception;

public abstract class BaseException extends RuntimeException implements IException {

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
        this(errorCode.getCode(), errorCode.getMessage());
    }

    protected BaseException(ErrorCode errorCode, Throwable cause) {
        this(errorCode.getCode(), errorCode.getMessage(), cause);
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
