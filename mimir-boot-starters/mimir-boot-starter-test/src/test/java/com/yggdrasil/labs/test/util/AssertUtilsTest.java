package com.yggdrasil.labs.test.util;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AssertUtils 断言工具类测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class AssertUtilsTest {

    // ========== assertContains 测试 ==========

    @Test
    void testAssertContains_Success() {
        List<String> list = Arrays.asList("a", "b", "c");
        AssertUtils.assertContains(list, "a");
        AssertUtils.assertContains(list, "b");
        AssertUtils.assertContains(list, "c");
    }

    @Test
    void testAssertContains_WithMessage_Success() {
        List<String> list = Arrays.asList("a", "b", "c");
        AssertUtils.assertContains(list, "a", "测试消息");
    }

    @Test
    void testAssertContains_NullCollection() {
        List<String> list = null;
        assertThrows(AssertionError.class, () -> {
            AssertUtils.assertContains(list, "a");
        }, "null 集合应抛出异常");
    }

    @Test
    void testAssertContains_EmptyCollection() {
        List<String> list = new ArrayList<>();
        assertThrows(AssertionError.class, () -> {
            AssertUtils.assertContains(list, "a");
        }, "空集合应抛出异常");
    }

    @Test
    void testAssertContains_ElementNotInCollection() {
        List<String> list = Arrays.asList("a", "b", "c");
        assertThrows(AssertionError.class, () -> {
            AssertUtils.assertContains(list, "d");
        }, "不存在的元素应抛出异常");
    }

    @Test
    void testAssertContains_WithMessage_NullCollection() {
        List<String> list = null;
        assertThrows(AssertionError.class, () -> {
            AssertUtils.assertContains(list, "a", "自定义消息");
        }, "null 集合应抛出异常");
    }

    @Test
    void testAssertContains_WithDifferentTypes() {
        List<Integer> list = Arrays.asList(1, 2, 3);
        AssertUtils.assertContains(list, 2);
    }

    // ========== assertContainsKey 测试 ==========

    @Test
    void testAssertContainsKey_Success() {
        Map<String, String> map = new HashMap<>();
        map.put("key1", "value1");
        map.put("key2", "value2");

        AssertUtils.assertContainsKey(map, "key1");
        AssertUtils.assertContainsKey(map, "key2");
    }

    @Test
    void testAssertContainsKey_WithMessage_Success() {
        Map<String, String> map = new HashMap<>();
        map.put("key1", "value1");

        AssertUtils.assertContainsKey(map, "key1", "测试消息");
    }

    @Test
    void testAssertContainsKey_NullMap() {
        Map<String, String> map = null;
        assertThrows(AssertionError.class, () -> {
            AssertUtils.assertContainsKey(map, "key1");
        }, "null Map 应抛出异常");
    }

    @Test
    void testAssertContainsKey_KeyNotInMap() {
        Map<String, String> map = new HashMap<>();
        map.put("key1", "value1");

        assertThrows(AssertionError.class, () -> {
            AssertUtils.assertContainsKey(map, "key2");
        }, "不存在的 key 应抛出异常");
    }

    @Test
    void testAssertContainsKey_WithMessage_NullMap() {
        Map<String, String> map = null;
        assertThrows(AssertionError.class, () -> {
            AssertUtils.assertContainsKey(map, "key1", "自定义消息");
        }, "null Map 应抛出异常");
    }

    @Test
    void testAssertContainsKey_WithDifferentKeyTypes() {
        Map<Integer, String> map = new HashMap<>();
        map.put(1, "value1");
        map.put(2, "value2");

        AssertUtils.assertContainsKey(map, 1);
        AssertUtils.assertContainsKey(map, 2);
    }

    // ========== assertNotBlank 测试 ==========

    @Test
    void testAssertNotBlank_Success() {
        AssertUtils.assertNotBlank("test");
        AssertUtils.assertNotBlank("  test  ");
        AssertUtils.assertNotBlank("a");
    }

    @Test
    void testAssertNotBlank_WithMessage_Success() {
        AssertUtils.assertNotBlank("test", "测试消息");
    }

    @Test
    void testAssertNotBlank_NullString() {
        assertThrows(AssertionError.class, () -> {
            AssertUtils.assertNotBlank(null);
        }, "null 字符串应抛出异常");
    }

    @Test
    void testAssertNotBlank_EmptyString() {
        assertThrows(AssertionError.class, () -> {
            AssertUtils.assertNotBlank("");
        }, "空字符串应抛出异常");
    }

    @Test
    void testAssertNotBlank_BlankString() {
        assertThrows(AssertionError.class, () -> {
            AssertUtils.assertNotBlank("   ");
        }, "空白字符串应抛出异常");

        assertThrows(AssertionError.class, () -> {
            AssertUtils.assertNotBlank("\t");
        }, "制表符字符串应抛出异常");

        assertThrows(AssertionError.class, () -> {
            AssertUtils.assertNotBlank("\n");
        }, "换行符字符串应抛出异常");
    }

    @Test
    void testAssertNotBlank_WithMessage_NullString() {
        assertThrows(AssertionError.class, () -> {
            AssertUtils.assertNotBlank(null, "自定义消息");
        }, "null 字符串应抛出异常");
    }

    // ========== assertEquals 测试 ==========

    @Test
    void testAssertEquals_BothNotNull_Success() {
        AssertUtils.assertEquals("test", "test");
        AssertUtils.assertEquals(1, 1);
        AssertUtils.assertEquals(true, true);
    }

    @Test
    void testAssertEquals_WithMessage_Success() {
        AssertUtils.assertEquals("test", "test", "测试消息");
        AssertUtils.assertEquals(1, 1, "测试消息");
    }

    @Test
    void testAssertEquals_BothNull_Success() {
        AssertUtils.assertEquals(null, null);
    }

    @Test
    void testAssertEquals_ExpectedNull_ActualNotNull() {
        assertThrows(AssertionError.class, () -> {
            AssertUtils.assertEquals(null, "test");
        }, "期望 null 但实际不为 null 应抛出异常");
    }

    @Test
    void testAssertEquals_ExpectedNotNull_ActualNull() {
        assertThrows(AssertionError.class, () -> {
            AssertUtils.assertEquals("test", null);
        }, "期望不为 null 但实际为 null 应抛出异常");
    }

    @Test
    void testAssertEquals_NotEqual() {
        assertThrows(AssertionError.class, () -> {
            AssertUtils.assertEquals("test1", "test2");
        }, "不相等应抛出异常");

        assertThrows(AssertionError.class, () -> {
            AssertUtils.assertEquals(1, 2);
        }, "不相等应抛出异常");
    }

    @Test
    void testAssertEquals_WithMessage_NotEqual() {
        assertThrows(AssertionError.class, () -> {
            AssertUtils.assertEquals("test1", "test2", "自定义消息");
        }, "不相等应抛出异常");
    }

    @Test
    void testAssertEquals_WithObjects() {
        Object obj1 = new Object();
        Object obj2 = obj1;
        AssertUtils.assertEquals(obj1, obj2);
    }

    @Test
    void testAssertEquals_WithCollections() {
        List<String> list1 = Arrays.asList("a", "b", "c");
        List<String> list2 = Arrays.asList("a", "b", "c");
        AssertUtils.assertEquals(list1, list2);
    }
}

