// 兼容性 fixture：按 v2.3.0 标签 mimir-boot-common 公共签名去 Lombok 后的最小历史源码，仅用于编译旧消费者。
package com.yggdrasil.labs.common.response;

import java.io.Serializable;

public class R<T extends Serializable> implements Serializable {

    private String code;
    private String message;
    private T data;
    private Long timestamp;
    private String traceId;

    public R() {
        this.timestamp = System.currentTimeMillis();
    }

    public R(String code, String message, T data) {
        this();
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T extends Serializable> R<T> success() {
        return new R<>("00000", "操作成功", null);
    }

    public static <T extends Serializable> R<T> success(T data) {
        return new R<>("00000", "操作成功", data);
    }

    public static <T extends Serializable> R<T> success(String message, T data) {
        return new R<>("00000", message, data);
    }

    public static <T extends Serializable> R<T> fail(String code, String message) {
        return new R<>(code, message, null);
    }

    public static <T extends Serializable> R<T> fail(String message) {
        return new R<>("00001", message, null);
    }

    public boolean isSuccess() {
        return "00000".equals(code);
    }

    public boolean isFail() {
        return !isSuccess();
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}
