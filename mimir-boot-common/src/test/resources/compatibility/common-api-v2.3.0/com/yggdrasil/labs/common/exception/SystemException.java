// 兼容性 fixture：按 v2.3.0 标签 mimir-boot-common 公共签名去 Lombok 后的最小历史源码，仅用于编译旧消费者。
package com.yggdrasil.labs.common.exception;

public class SystemException extends BaseException {

    public SystemException(String code, String message) {
        super(code, message);
    }

    public SystemException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public SystemException(ErrorCode errorCode) {
        super(errorCode);
    }

    public SystemException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
