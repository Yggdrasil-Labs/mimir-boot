package com.yggdrasil.labs.mybatis.util;

import com.yggdrasil.labs.mybatis.annotation.SensitiveField;

/**
 * SQL 参数脱敏工具。
 *
 * <p>根据字段上的 {@link SensitiveField} 注解和内置敏感参数名进行定向脱敏；对于基础类型与常见简单类型，直接透传。
 */
public class SqlLogMaskUtils {
    private SqlLogMaskUtils() {
        throw new IllegalStateException("Utility class");
    }

    /** 按字段注解和敏感参数名递归脱敏，使用引用身份检测循环。 */
    public static Object maskParams(Object params) {
        return SqlParameterMasker.mask(params);
    }

    /** 对 SQL 文本中的敏感赋值进行脱敏，避免常量值绕过参数对象的脱敏路径。 */
    public static String maskSql(String sql) {
        return SqlTextMasker.mask(sql);
    }
}
