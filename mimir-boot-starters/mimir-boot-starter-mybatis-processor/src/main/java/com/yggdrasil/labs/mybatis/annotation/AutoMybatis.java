package com.yggdrasil.labs.mybatis.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * 标注在实体类上，在编译期自动生成 Mapper、Service、ServiceImpl。
 */
@Target(TYPE)
@Retention(SOURCE)
@Documented
public @interface AutoMybatis {
    String mapperPackage() default "mapper";

    String servicePackage() default "service";

    String serviceImplPackage() default "service.impl";

    String mapperSuffix() default "Mapper";

    String serviceSuffix() default "Service";

    String serviceImplSuffix() default "ServiceImpl";
}


