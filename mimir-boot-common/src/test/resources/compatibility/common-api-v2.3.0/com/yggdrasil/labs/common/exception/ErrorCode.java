// 兼容性 fixture：按 v2.3.0 标签 mimir-boot-common 公共签名去 Lombok 后的最小历史源码，仅用于编译旧消费者。
package com.yggdrasil.labs.common.exception;

public enum ErrorCode {
    SUCCESS("00000", "操作成功"),
    FAIL("00001", "操作失败"),
    SYSTEM_ERROR("10000", "系统错误"),
    DATA_NOT_FOUND("20001", "数据不存在");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public static ErrorCode fromCode(String code) {
        return SYSTEM_ERROR;
    }

    public static ErrorCode fromCodeOrNull(String code) {
        return null;
    }
}
