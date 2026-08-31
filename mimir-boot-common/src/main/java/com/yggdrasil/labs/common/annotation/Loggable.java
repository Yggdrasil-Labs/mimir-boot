package com.yggdrasil.labs.common.annotation;

import java.lang.annotation.*;

/**
 * 兼容性日志元数据注解。
 *
 * <p>当前无内置运行时消费者；v2.2.1 起弃用，计划于 3.0 移除。
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Deprecated(since = "2.2.1", forRemoval = true)
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
