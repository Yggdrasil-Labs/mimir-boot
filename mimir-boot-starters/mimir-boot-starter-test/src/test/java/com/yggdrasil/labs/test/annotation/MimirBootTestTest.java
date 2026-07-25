package com.yggdrasil.labs.test.annotation;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AliasFor;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MimirBootTest 注解测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class MimirBootTestTest {

    /**
     * 测试使用 @MimirBootTest 注解的类
     */
    @MimirBootTest
    static class TestClassWithAnnotation {
    }

    /**
     * 测试使用 @MimirBootTest 注解并自定义属性的类
     */
    @MimirBootTest(
            classes = {String.class},
            properties = {"test.property=value"},
            webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
    )
    static class TestClassWithCustomProperties {
    }

    @Test
    void testMimirBootTestAnnotation_Present() {
        // 验证注解存在
        assertTrue(TestClassWithAnnotation.class.isAnnotationPresent(MimirBootTest.class),
                "类应包含 @MimirBootTest 注解");
    }

    @Test
    void testMimirBootTestAnnotation_MetaAnnotations() {
        // 验证元注解（使用 AnnotatedElementUtils 来查找组合注解中的元注解）
        assertTrue(AnnotatedElementUtils.isAnnotated(TestClassWithAnnotation.class, SpringBootTest.class),
                "@MimirBootTest 应包含 @SpringBootTest");
        assertTrue(AnnotatedElementUtils.isAnnotated(TestClassWithAnnotation.class, ActiveProfiles.class),
                "@MimirBootTest 应包含 @ActiveProfiles");

        // 验证 ActiveProfiles 的值（使用 AnnotationUtils 来获取组合注解中的元注解）
        ActiveProfiles activeProfiles = AnnotationUtils.findAnnotation(TestClassWithAnnotation.class, ActiveProfiles.class);
        assertNotNull(activeProfiles, "ActiveProfiles 注解不应为 null");
        assertArrayEquals(new String[]{"test"}, activeProfiles.value(),
                "ActiveProfiles 值应为 ['test']");
    }

    @Test
    void testMimirBootTestAnnotation_DefaultValues() {
        MimirBootTest annotation = TestClassWithAnnotation.class.getAnnotation(MimirBootTest.class);
        assertNotNull(annotation, "注解不应为 null");

        // 验证默认值
        assertEquals(0, annotation.classes().length, "classes 默认应为空数组");
        assertEquals(0, annotation.properties().length, "properties 默认应为空数组");
        assertEquals(SpringBootTest.WebEnvironment.MOCK, annotation.webEnvironment(),
                "webEnvironment 默认应为 MOCK");
    }

    @Test
    void testMimirBootTestAnnotation_CustomValues() {
        MimirBootTest annotation = TestClassWithCustomProperties.class.getAnnotation(MimirBootTest.class);
        assertNotNull(annotation, "注解不应为 null");

        // 验证自定义值
        assertEquals(1, annotation.classes().length, "classes 应包含 1 个类");
        assertEquals(String.class, annotation.classes()[0], "classes 应包含 String.class");
        assertEquals(1, annotation.properties().length, "properties 应包含 1 个属性");
        assertEquals("test.property=value", annotation.properties()[0], "properties 应包含指定值");
        assertEquals(SpringBootTest.WebEnvironment.RANDOM_PORT, annotation.webEnvironment(),
                "webEnvironment 应为 RANDOM_PORT");
    }

    @Test
    void testMimirBootTestAnnotation_KeepsDeprecatedCompatibilityAttributes() {
        var useDefaultFilters = java.util.Arrays.stream(MimirBootTest.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("useDefaultFilters"))
                .findFirst();
        var excludeAutoConfiguration = java.util.Arrays.stream(MimirBootTest.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("excludeAutoConfiguration"))
                .findFirst();

        assertTrue(useDefaultFilters.isPresent(), "应保留 useDefaultFilters 以兼容既有测试源码");
        assertEquals(true, useDefaultFilters.orElseThrow().getDefaultValue());
        assertTrue(useDefaultFilters.orElseThrow().isAnnotationPresent(Deprecated.class));
        assertTrue(excludeAutoConfiguration.isPresent(),
                "应保留 excludeAutoConfiguration 以兼容既有测试源码");
        assertArrayEquals(new Class<?>[0],
                (Class<?>[]) excludeAutoConfiguration.orElseThrow().getDefaultValue());
        assertTrue(excludeAutoConfiguration.orElseThrow().isAnnotationPresent(Deprecated.class));
    }

    @Test
    void testMimirBootTestAnnotation_UsesExplicitSpringBootAliases() throws NoSuchMethodException {
        assertAliasForSpringBootTest("classes", "classes");
        assertAliasForSpringBootTest("properties", "properties");
        assertAliasForSpringBootTest("webEnvironment", "webEnvironment");
    }

    private void assertAliasForSpringBootTest(String methodName, String targetAttribute) throws NoSuchMethodException {
        AliasFor aliasFor = MimirBootTest.class.getDeclaredMethod(methodName).getAnnotation(AliasFor.class);

        assertNotNull(aliasFor, methodName + " 应使用 @AliasFor 显式覆盖 SpringBootTest 属性");
        assertEquals(SpringBootTest.class, aliasFor.annotation());
        assertEquals(targetAttribute, aliasFor.attribute());
    }

    @Test
    void testMimirBootTestAnnotation_Inherited() {
        // 验证 @Inherited 元注解
        Inherited inherited = MimirBootTest.class.getAnnotation(Inherited.class);
        assertNotNull(inherited, "@MimirBootTest 应包含 @Inherited 元注解");
    }

    @Test
    void testMimirBootTestAnnotation_Target() {
        // 验证 @Target 元注解
        Target target = MimirBootTest.class.getAnnotation(Target.class);
        assertNotNull(target, "@MimirBootTest 应包含 @Target 元注解");
        assertArrayEquals(new ElementType[]{ElementType.TYPE}, target.value(),
                "@Target 值应为 ElementType.TYPE");
    }

    @Test
    void testMimirBootTestAnnotation_Retention() {
        // 验证 @Retention 元注解
        Retention retention = MimirBootTest.class.getAnnotation(Retention.class);
        assertNotNull(retention, "@MimirBootTest 应包含 @Retention 元注解");
        assertEquals(RetentionPolicy.RUNTIME, retention.value(),
                "@Retention 值应为 RetentionPolicy.RUNTIME");
    }
}
