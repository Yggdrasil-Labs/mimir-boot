package com.yggdrasil.labs.mybatis.processor;

import com.yggdrasil.labs.test.base.BaseUnitTest;
import com.yggdrasil.labs.test.util.AssertUtils;
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
class AutoMybatisProcessorUtilTest extends BaseUnitTest {

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
        AssertUtils.assertEquals("com.example.mapper", testJoinPackage("com.example", "mapper"));
        AssertUtils.assertEquals("com.example.service", testJoinPackage("com.example", "service"));
        AssertUtils.assertEquals("com.example.service.impl", testJoinPackage("com.example", "service.impl"));
    }

    @Test
    void testJoinPackageWithEmptyBase() {
        AssertUtils.assertEquals("mapper", testJoinPackage("", "mapper"));
        AssertUtils.assertEquals("service", testJoinPackage("", "service"));
    }

    @Test
    void testJoinPackageWithNullBase() {
        AssertUtils.assertEquals("mapper", testJoinPackage(null, "mapper"));
        AssertUtils.assertEquals("service", testJoinPackage(null, "service"));
    }

    @Test
    void testJoinPackageWithEmptySub() {
        AssertUtils.assertEquals("com.example", testJoinPackage("com.example", ""));
        AssertUtils.assertEquals("com.example", testJoinPackage("com.example", null));
    }

    @Test
    void testJoinPackageWithBothEmpty() {
        // 当 base 为空时，返回 sub
        // 当 sub 也为空时，返回 null（因为 sub 是 null）
        AssertUtils.assertEquals("", testJoinPackage("", ""));
        assertNull(testJoinPackage(null, null));
        assertNull(testJoinPackage("", null));
        AssertUtils.assertEquals("", testJoinPackage(null, ""));
    }

    @Test
    void testJoinPackageWithComplexPackages() {
        AssertUtils.assertEquals("com.yggdrasil.labs.mybatis.mapper", 
                     testJoinPackage("com.yggdrasil.labs.mybatis", "mapper"));
        AssertUtils.assertEquals("com.yggdrasil.labs.mybatis.service.impl", 
                     testJoinPackage("com.yggdrasil.labs.mybatis", "service.impl"));
    }

    @Test
    void testJoinPackageWithSingleLevelBase() {
        AssertUtils.assertEquals("example.mapper", testJoinPackage("example", "mapper"));
    }

    @Test
    void testJoinPackageWithSubStartingWithDot() {
        // 注意：如果 sub 以 . 开头，joinPackage 不会处理这种情况
        // 这只是测试当前实现的边界情况
        AssertUtils.assertEquals("com.example..mapper", testJoinPackage("com.example", ".mapper"));
    }

    // 使用反射调用 private static 方法进行测试
    private static String testRemoveDoSuffix(String className) {
        try {
            Method method = AutoMybatisProcessor.class.getDeclaredMethod("removeDoSuffix", String.class);
            method.setAccessible(true);
            return (String) method.invoke(null, className);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke removeDoSuffix method", e);
        }
    }

    @Test
    void testRemoveDoSuffix_WithDoSuffix() {
        AssertUtils.assertEquals("User", testRemoveDoSuffix("UserDO"));
        AssertUtils.assertEquals("Order", testRemoveDoSuffix("OrderDO"));
        AssertUtils.assertEquals("Product", testRemoveDoSuffix("ProductDO"));
    }

    @Test
    void testRemoveDoSuffix_WithoutDoSuffix() {
        AssertUtils.assertEquals("User", testRemoveDoSuffix("User"));
        AssertUtils.assertEquals("Order", testRemoveDoSuffix("Order"));
        AssertUtils.assertEquals("Product", testRemoveDoSuffix("Product"));
    }

    @Test
    void testRemoveDoSuffix_WithOnlyDO() {
        // 如果类名就是 "DO"，不应该被处理（长度 <= 2）
        AssertUtils.assertEquals("DO", testRemoveDoSuffix("DO"));
    }

    @Test
    void testRemoveDoSuffix_WithNull() {
        assertNull(testRemoveDoSuffix(null));
    }

    @Test
    void testRemoveDoSuffix_WithEmptyString() {
        AssertUtils.assertEquals("", testRemoveDoSuffix(""));
    }

    @Test
    void testRemoveDoSuffix_WithCaseSensitive() {
        // 只处理大写的 DO，不处理小写的 do
        AssertUtils.assertEquals("Userdo", testRemoveDoSuffix("Userdo"));
        AssertUtils.assertEquals("UserDo", testRemoveDoSuffix("UserDo"));
        AssertUtils.assertEquals("User", testRemoveDoSuffix("UserDO"));
    }
}

