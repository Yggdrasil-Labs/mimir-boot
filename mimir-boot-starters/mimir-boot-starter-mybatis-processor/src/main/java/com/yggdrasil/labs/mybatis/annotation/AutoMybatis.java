package com.yggdrasil.labs.mybatis.annotation;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/** 标注在实体类上，在编译期自动生成 Mapper、Service、ServiceImpl。 */
@Target(TYPE)
@Retention(SOURCE)
@Documented
public @interface AutoMybatis {
    /** 生成 Mapper 所在包名。 */
    String mapperPackage() default "mapper";

    /** 生成 Service 所在包名。 */
    String servicePackage() default "service";

    /** 生成 ServiceImpl 所在包名。 */
    String serviceImplPackage() default "service.impl";

    /** 生成 Mapper 的类型名后缀。 */
    String mapperSuffix() default "Mapper";

    /** 生成 Service 的类型名后缀。 */
    String serviceSuffix() default "Service";

    /** 生成 ServiceImpl 的类型名后缀。 */
    String serviceImplSuffix() default "ServiceImpl";
}
