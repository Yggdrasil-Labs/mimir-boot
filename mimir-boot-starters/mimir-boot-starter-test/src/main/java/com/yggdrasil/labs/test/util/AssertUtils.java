package com.yggdrasil.labs.test.util;

import org.junit.jupiter.api.Assertions;

import java.util.Collection;
import java.util.Map;

/**
 * 断言工具类
 *
 * <p>提供增强的断言方法，简化测试代码</p>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
public final class AssertUtils {

    private AssertUtils() {
        // 工具类，禁止实例化
    }

    /**
     * 断言集合不为空且包含指定元素
     *
     * @param collection 集合
     * @param element    元素
     * @param message    错误消息
     */
    public static <T> void assertContains(Collection<T> collection, T element, String message) {
        Assertions.assertNotNull(collection, message + ": 集合不能为 null");
        Assertions.assertFalse(collection.isEmpty(), message + ": 集合不能为空");
        Assertions.assertTrue(collection.contains(element), message + ": 集合应包含元素 " + element);
    }

    /**
     * 断言集合不为空且包含指定元素
     *
     * @param collection 集合
     * @param element    元素
     */
    public static <T> void assertContains(Collection<T> collection, T element) {
        assertContains(collection, element, "集合断言失败");
    }

    /**
     * 断言 Map 包含指定 key
     *
     * @param map     Map
     * @param key     key
     * @param message 错误消息
     */
    public static <K, V> void assertContainsKey(Map<K, V> map, K key, String message) {
        Assertions.assertNotNull(map, message + ": Map 不能为 null");
        Assertions.assertTrue(map.containsKey(key), message + ": Map 应包含 key " + key);
    }

    /**
     * 断言 Map 包含指定 key
     *
     * @param map Map
     * @param key key
     */
    public static <K, V> void assertContainsKey(Map<K, V> map, K key) {
        assertContainsKey(map, key, "Map 断言失败");
    }

    /**
     * 断言字符串不为空且不为空白
     *
     * @param str     字符串
     * @param message 错误消息
     */
    public static void assertNotBlank(String str, String message) {
        Assertions.assertNotNull(str, message + ": 字符串不能为 null");
        Assertions.assertFalse(str.trim().isEmpty(), message + ": 字符串不能为空");
    }

    /**
     * 断言字符串不为空且不为空白
     *
     * @param str 字符串
     */
    public static void assertNotBlank(String str) {
        assertNotBlank(str, "字符串断言失败");
    }

    /**
     * 断言两个对象相等（处理 null 情况）
     *
     * @param expected 期望值
     * @param actual   实际值
     * @param message  错误消息
     */
    public static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null) {
            Assertions.assertNull(actual, message);
        } else {
            Assertions.assertEquals(expected, actual, message);
        }
    }

    /**
     * 断言两个对象相等（处理 null 情况）
     *
     * @param expected 期望值
     * @param actual   实际值
     */
    public static void assertEquals(Object expected, Object actual) {
        assertEquals(expected, actual, "对象不相等");
    }
}

