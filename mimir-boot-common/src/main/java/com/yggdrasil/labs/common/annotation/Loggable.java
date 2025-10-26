package com.yggdrasil.labs.common.annotation;

import java.lang.annotation.*;

/**
 * 统一日志记录注解
 * 用于标记需要记录日志的方法
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Loggable {

    /**
     * 操作模块
     */
    String module() default "";

    /**
     * 操作类型（如：INSERT, UPDATE, DELETE等）
     */
    String type() default "";

    /**
     * 操作描述
     */
    String description() default "";

    /**
     * 是否记录请求参数
     */
    boolean logRequest() default true;

    /**
     * 是否记录响应结果
     */
    boolean logResponse() default true;

    /**
     * 是否记录执行时间
     */
    boolean logExecutionTime() default true;
}
