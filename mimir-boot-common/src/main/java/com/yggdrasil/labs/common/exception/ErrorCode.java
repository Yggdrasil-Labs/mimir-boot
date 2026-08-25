package com.yggdrasil.labs.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 错误码枚举
 * 
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {
    
    // ==================== 通用错误码 ====================

    /** 操作成功 */
    SUCCESS("00000", "操作成功"),
    /** 操作失败 */
    FAIL("00001", "操作失败"),
    /** 参数错误 */
    PARAM_ERROR("00002", "参数错误"),
    /** 参数缺失 */
    PARAM_MISSING("00003", "参数缺失"),
    /** 参数无效 */
    PARAM_INVALID("00004", "参数无效"),

    // ==================== 系统错误码 ====================

    /** 系统错误 */
    SYSTEM_ERROR("10000", "系统错误"),
    /** 系统繁忙 */
    SYSTEM_BUSY("10001", "系统繁忙"),
    /** 系统超时 */
    SYSTEM_TIMEOUT("10002", "系统超时"),
    /** 系统不可用 */
    SYSTEM_UNAVAILABLE("10003", "系统不可用"),

    // ==================== 业务错误码 ====================

    /** 业务错误 */
    BUSINESS_ERROR("20000", "业务错误"),
    /** 数据不存在 */
    DATA_NOT_FOUND("20001", "数据不存在"),
    /** 数据已存在 */
    DATA_ALREADY_EXISTS("20002", "数据已存在"),
    /** 数据无效 */
    DATA_INVALID("20003", "数据无效"),
    /** 操作不允许 */
    OPERATION_NOT_ALLOWED("20004", "操作不允许"),

    // ==================== 权限错误码 ====================

    /** 未授权 */
    UNAUTHORIZED("30000", "未授权"),
    /** 禁止访问 */
    FORBIDDEN("30001", "禁止访问"),
    /** 令牌无效 */
    TOKEN_INVALID("30002", "令牌无效"),
    /** 令牌过期 */
    TOKEN_EXPIRED("30003", "令牌过期"),
    /** 权限不足 */
    PERMISSION_DENIED("30004", "权限不足"),

    // ==================== 网络错误码 ====================

    /** 网络错误 */
    NETWORK_ERROR("40000", "网络错误"),
    /** 网络超时 */
    NETWORK_TIMEOUT("40001", "网络超时"),
    /** 网络不可用 */
    NETWORK_UNAVAILABLE("40002", "网络不可用"),

    // ==================== 第三方服务错误码 ====================

    /** 第三方服务错误 */
    THIRD_PARTY_ERROR("50000", "第三方服务错误"),
    /** 第三方服务超时 */
    THIRD_PARTY_TIMEOUT("50001", "第三方服务超时"),
    /** 第三方服务不可用 */
    THIRD_PARTY_UNAVAILABLE("50002", "第三方服务不可用");
    
    /**
     * 错误码
     */
    private final String code;
    
    /**
     * 错误信息
     */
    private final String message;
    
    /**
     * 根据错误码获取错误码枚举
     * 
     * @param code 错误码
     * @return 错误码枚举
     */
    public static ErrorCode fromCode(String code) {
        for (ErrorCode errorCode : values()) {
            if (errorCode.getCode().equals(code)) {
                return errorCode;
            }
        }
        return SYSTEM_ERROR;
    }

    /**
     * 根据错误码获取枚举，未知错误码返回 null
     *
     * @param code 错误码
     * @return 错误码枚举或 null
     */
    public static ErrorCode fromCodeOrNull(String code) {
        if (code == null) {
            return null;
        }
        for (ErrorCode errorCode : values()) {
            if (errorCode.getCode().equals(code)) {
                return errorCode;
            }
        }
        return null;
    }
}
