package com.yggdrasil.labs.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 删除标志枚举
 * 
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@Getter
@AllArgsConstructor
public enum DeleteFlag {
    
    /**
     * 未删除
     */
    NOT_DELETED(0, "未删除"),
    
    /**
     * 已删除
     */
    DELETED(1, "已删除");
    
    /**
     * 删除标志码
     */
    private final Integer code;
    
    /**
     * 删除标志描述
     */
    private final String description;
    
    /**
     * 根据删除标志码获取枚举
     * 
     * @param code 删除标志码
     * @return 删除标志枚举
     */
    public static DeleteFlag fromCode(Integer code) {
        for (DeleteFlag flag : values()) {
            if (flag.getCode().equals(code)) {
                return flag;
            }
        }
        return NOT_DELETED;
    }
    
    /**
     * 判断是否已删除
     * 
     * @param code 删除标志码
     * @return 是否已删除
     */
    public static boolean isDeleted(Integer code) {
        return DELETED.getCode().equals(code);
    }
    
    /**
     * 判断是否未删除
     * 
     * @param code 删除标志码
     * @return 是否未删除
     */
    public static boolean isNotDeleted(Integer code) {
        return NOT_DELETED.getCode().equals(code);
    }
}
