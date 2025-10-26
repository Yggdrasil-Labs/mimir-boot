package com.yggdrasil.labs.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 操作类型枚举
 * 
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@Getter
@AllArgsConstructor
public enum OperationType {
    
    /**
     * 查询
     */
    SELECT("SELECT", "查询"),
    
    /**
     * 新增
     */
    INSERT("INSERT", "新增"),
    
    /**
     * 更新
     */
    UPDATE("UPDATE", "更新"),
    
    /**
     * 删除
     */
    DELETE("DELETE", "删除"),
    
    /**
     * 导入
     */
    IMPORT("IMPORT", "导入"),
    
    /**
     * 导出
     */
    EXPORT("EXPORT", "导出"),
    
    /**
     * 登录
     */
    LOGIN("LOGIN", "登录"),
    
    /**
     * 登出
     */
    LOGOUT("LOGOUT", "登出"),
    
    /**
     * 其他
     */
    OTHER("OTHER", "其他");
    
    /**
     * 操作类型码
     */
    private final String code;
    
    /**
     * 操作类型描述
     */
    private final String description;
    
    /**
     * 根据操作类型码获取枚举
     * 
     * @param code 操作类型码
     * @return 操作类型枚举
     */
    public static OperationType fromCode(String code) {
        for (OperationType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return OTHER;
    }
}
