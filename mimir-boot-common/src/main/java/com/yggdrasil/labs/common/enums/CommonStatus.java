package com.yggdrasil.labs.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通用状态枚举
 * 
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@Getter
@AllArgsConstructor
public enum CommonStatus {
    
    /**
     * 启用
     */
    ENABLED(1, "启用"),
    
    /**
     * 禁用
     */
    DISABLED(0, "禁用");
    
    /**
     * 状态码
     */
    private final Integer code;
    
    /**
     * 状态描述
     */
    private final String description;
    
    /**
     * 根据状态码获取枚举
     * 
     * @param code 状态码
     * @return 状态枚举
     */
    public static CommonStatus fromCode(Integer code) {
        if (code == null) {
            return DISABLED;
        }
        for (CommonStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return DISABLED;
    }
    
    /**
     * 判断是否为启用状态
     * 
     * @param code 状态码
     * @return 是否为启用状态
     */
    public static boolean isEnabled(Integer code) {
        return ENABLED.getCode().equals(code);
    }
    
    /**
     * 判断是否为禁用状态
     * 
     * @param code 状态码
     * @return 是否为禁用状态
     */
    public static boolean isDisabled(Integer code) {
        return DISABLED.getCode().equals(code);
    }
    
}
