package com.yggdrasil.labs.mybatis.annotation;

import com.yggdrasil.labs.test.base.BaseUnitTest;
import com.yggdrasil.labs.test.util.AssertUtils;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AutoMybatis 注解测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class AutoMybatisTest extends BaseUnitTest {

    @Test
    void testAnnotationRetention() {
        // 验证注解保留策略为 SOURCE（编译期）
        Retention retention = AutoMybatis.class.getAnnotation(Retention.class);
        assertNotNull(retention);
        AssertUtils.assertEquals(java.lang.annotation.RetentionPolicy.SOURCE, retention.value());
    }

    @Test
    void testAnnotationTarget() {
        // 验证注解只能用于类型（类、接口、枚举等）
        Target target = AutoMybatis.class.getAnnotation(Target.class);
        assertNotNull(target);
        assertArrayEquals(new java.lang.annotation.ElementType[]{java.lang.annotation.ElementType.TYPE}, target.value());
    }

    @Test
    void testAnnotationDocumented() {
        // 验证注解被 @Documented 标记
        Documented documented = AutoMybatis.class.getAnnotation(Documented.class);
        assertNotNull(documented);
    }

    @Test
    void testDefaultValues() throws NoSuchMethodException {
        // 验证默认值
        // 注意：@AutoMybatis 是 SOURCE 级别的注解，运行时无法获取注解实例
        // 但我们可以通过反射获取注解方法的默认值
        Class<AutoMybatis> annotationClass = AutoMybatis.class;

        Method mapperPackageMethod = annotationClass.getMethod("mapperPackage");
        Object mapperPackageDefault = mapperPackageMethod.getDefaultValue();
        AssertUtils.assertEquals("mapper", mapperPackageDefault);

        Method servicePackageMethod = annotationClass.getMethod("servicePackage");
        Object servicePackageDefault = servicePackageMethod.getDefaultValue();
        AssertUtils.assertEquals("service", servicePackageDefault);

        Method serviceImplPackageMethod = annotationClass.getMethod("serviceImplPackage");
        Object serviceImplPackageDefault = serviceImplPackageMethod.getDefaultValue();
        AssertUtils.assertEquals("service.impl", serviceImplPackageDefault);

        Method mapperSuffixMethod = annotationClass.getMethod("mapperSuffix");
        Object mapperSuffixDefault = mapperSuffixMethod.getDefaultValue();
        AssertUtils.assertEquals("Mapper", mapperSuffixDefault);

        Method serviceSuffixMethod = annotationClass.getMethod("serviceSuffix");
        Object serviceSuffixDefault = serviceSuffixMethod.getDefaultValue();
        AssertUtils.assertEquals("Service", serviceSuffixDefault);

        Method serviceImplSuffixMethod = annotationClass.getMethod("serviceImplSuffix");
        Object serviceImplSuffixDefault = serviceImplSuffixMethod.getDefaultValue();
        AssertUtils.assertEquals("ServiceImpl", serviceImplSuffixDefault);
    }

    @Test
    void testAnnotationMethods() throws NoSuchMethodException {
        // 验证注解方法存在且可访问
        Class<AutoMybatis> annotationClass = AutoMybatis.class;

        assertNotNull(annotationClass.getMethod("mapperPackage"));
        assertNotNull(annotationClass.getMethod("servicePackage"));
        assertNotNull(annotationClass.getMethod("serviceImplPackage"));
        assertNotNull(annotationClass.getMethod("mapperSuffix"));
        assertNotNull(annotationClass.getMethod("serviceSuffix"));
        assertNotNull(annotationClass.getMethod("serviceImplSuffix"));
    }
}

