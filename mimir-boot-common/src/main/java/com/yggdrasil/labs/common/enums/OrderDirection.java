package com.yggdrasil.labs.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 排序方向枚举
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@Getter
@AllArgsConstructor
public enum OrderDirection {
    
    /**
     * 升序
     */
    ASC("ASC", "升序"),
    
    /**
     * 降序
     */
    DESC("DESC", "降序");
    
    /**
     * 方向码
     */
    private final String code;
    
    /**
     * 方向描述
     */
    private final String description;
    
    /**
     * 根据方向码获取枚举
     *
     * @param code 方向码
     * @return 方向枚举
     */
    public static OrderDirection fromCode(String code) {
        if (code == null) {
            return ASC;
        }
        for (OrderDirection direction : values()) {
            if (direction.getCode().equalsIgnoreCase(code)) {
                return direction;
            }
        }
        return ASC;
    }
    
    /**
     * 判断是否为升序
     *
     * @param code 方向码
     * @return 是否为升序
     */
    public static boolean isAsc(String code) {
        return ASC.getCode().equalsIgnoreCase(code);
    }
    
    /**
     * 判断是否为降序
     *
     * @param code 方向码
     * @return 是否为降序
     */
    public static boolean isDesc(String code) {
        return DESC.getCode().equalsIgnoreCase(code);
    }
}

