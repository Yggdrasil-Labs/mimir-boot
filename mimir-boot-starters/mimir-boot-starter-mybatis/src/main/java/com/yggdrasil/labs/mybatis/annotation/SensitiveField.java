package com.yggdrasil.labs.mybatis.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface SensitiveField {

    /**
     * 脱敏策略
     */
    MaskStrategy strategy() default MaskStrategy.ALL;

    /**
     * 自定义脱敏字符（当strategy为CUSTOM时有效）
     */
    String replacement() default "******";

    enum MaskStrategy {
        /** 全部脱敏 */
        ALL,
        /** 保留前3位后4位 */
        PHONE,
        /** 保留前6位后4位 */
        ID_CARD,
        /** 保留前4位后4位 */
        BANK_CARD,
        /** 邮箱脱敏 */
        EMAIL,
        /** 自定义 */
        CUSTOM
    }
}

