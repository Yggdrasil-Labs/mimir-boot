package com.yggdrasil.labs.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Loggable {

    String module() default "";

    String type() default "";

    String description() default "";

    boolean logRequest() default true;

    boolean logResponse() default true;

    boolean logExecutionTime() default true;
}
