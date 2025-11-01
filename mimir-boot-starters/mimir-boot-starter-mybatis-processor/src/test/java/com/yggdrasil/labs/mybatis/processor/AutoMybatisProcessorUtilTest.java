package com.yggdrasil.labs.mybatis.processor;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AutoMybatisProcessor 工具方法测试
 *
 * <p>测试 joinPackage 等辅助方法的逻辑</p>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class AutoMybatisProcessorUtilTest {

    // 使用反射调用 private static 方法进行测试
    private static String testJoinPackage(String base, String sub) {
        try {
            Method method = AutoMybatisProcessor.class.getDeclaredMethod("joinPackage", String.class, String.class);
            method.setAccessible(true);
            return (String) method.invoke(null, base, sub);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke joinPackage method", e);
        }
    }

    @Test
    void testJoinPackageWithBothNonEmpty() {
        assertEquals("com.example.mapper", testJoinPackage("com.example", "mapper"));
        assertEquals("com.example.service", testJoinPackage("com.example", "service"));
        assertEquals("com.example.service.impl", testJoinPackage("com.example", "service.impl"));
    }

    @Test
    void testJoinPackageWithEmptyBase() {
        assertEquals("mapper", testJoinPackage("", "mapper"));
        assertEquals("service", testJoinPackage("", "service"));
    }

    @Test
    void testJoinPackageWithNullBase() {
        assertEquals("mapper", testJoinPackage(null, "mapper"));
        assertEquals("service", testJoinPackage(null, "service"));
    }

    @Test
    void testJoinPackageWithEmptySub() {
        assertEquals("com.example", testJoinPackage("com.example", ""));
        assertEquals("com.example", testJoinPackage("com.example", null));
    }

    @Test
    void testJoinPackageWithBothEmpty() {
        // 当 base 为空时，返回 sub
        // 当 sub 也为空时，返回 null（因为 sub 是 null）
        assertEquals("", testJoinPackage("", ""));
        assertNull(testJoinPackage(null, null));
        assertNull(testJoinPackage("", null));
        assertEquals("", testJoinPackage(null, ""));
    }

    @Test
    void testJoinPackageWithComplexPackages() {
        assertEquals("com.yggdrasil.labs.mybatis.mapper", 
                     testJoinPackage("com.yggdrasil.labs.mybatis", "mapper"));
        assertEquals("com.yggdrasil.labs.mybatis.service.impl", 
                     testJoinPackage("com.yggdrasil.labs.mybatis", "service.impl"));
    }

    @Test
    void testJoinPackageWithSingleLevelBase() {
        assertEquals("example.mapper", testJoinPackage("example", "mapper"));
    }

    @Test
    void testJoinPackageWithSubStartingWithDot() {
        // 注意：如果 sub 以 . 开头，joinPackage 不会处理这种情况
        // 这只是测试当前实现的边界情况
        assertEquals("com.example..mapper", testJoinPackage("com.example", ".mapper"));
    }
}

