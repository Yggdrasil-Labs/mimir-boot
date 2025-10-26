package com.yggdrasil.labs.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.yggdrasil.labs.common.exception.ErrorCode;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一响应结果
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 响应码
     */
    private String code;

    /**
     * 响应信息
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 时间戳
     */
    private Long timestamp;

    /**
     * 请求追踪ID
     */
    private String traceId;

    /**
     * 构造方法
     */
    public ApiResponse() {
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 构造方法
     *
     * @param code    响应码
     * @param message 响应信息
     * @param data    响应数据
     */
    public ApiResponse(String code, String message, T data) {
        this();
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 成功响应
     *
     * @param <T> 数据类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), null);
    }

    /**
     * 成功响应
     *
     * @param data 数据
     * @param <T>  数据类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }

    /**
     * 成功响应
     *
     * @param message 消息
     * @param data    数据
     * @param <T>     数据类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), message, data);
    }

    /**
     * 失败响应
     *
     * @param code    错误码
     * @param message 错误信息
     * @param <T>     数据类型
     * @return 失败响应
     */
    public static <T> ApiResponse<T> fail(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    /**
     * 失败响应
     *
     * @param message 错误信息
     * @param <T>     数据类型
     * @return 失败响应
     */
    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(ErrorCode.FAIL.getCode(), message, null);
    }

    /**
     * 判断是否成功
     *
     * @return 是否成功
     */
    public boolean isSuccess() {
        return ErrorCode.SUCCESS.getCode().equals(this.code);
    }

    /**
     * 判断是否失败
     *
     * @return 是否失败
     */
    public boolean isFail() {
        return !isSuccess();
    }
}
