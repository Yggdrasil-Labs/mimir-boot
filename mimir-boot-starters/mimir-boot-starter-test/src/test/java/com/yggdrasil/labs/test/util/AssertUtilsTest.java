package com.yggdrasil.labs.test.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

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

    // ========== 日志断言测试 ==========

    private static final String TEST_LOGGER_NAME = "test.assert.logger";
    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void setUpLogger() {
        appender = LogTestUtils.setupLogger(TEST_LOGGER_NAME);
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        logger = context.getLogger(TEST_LOGGER_NAME);
    }

    @AfterEach
    void tearDownLogger() {
        LogTestUtils.cleanupLogger(logger, appender);
    }

    @Test
    void testAssertLogLevel_Success() {
        logger.info("Test message");
        ILoggingEvent event = appender.list.get(0);

        assertDoesNotThrow(() -> {
            AssertUtils.assertLogLevel(event, Level.INFO);
            AssertUtils.assertLogLevel(event, Level.INFO, "自定义消息");
        });
    }

    @Test
    void testAssertLogLevel_Failure() {
        logger.info("Test message");
        ILoggingEvent event = appender.list.get(0);

        assertThrows(AssertionError.class, () -> {
            AssertUtils.assertLogLevel(event, Level.ERROR);
        }, "日志级别不匹配应抛出异常");
    }

    @Test
    void testAssertLogLevel_NullEvent() {
        assertThrows(AssertionError.class, () -> {
            AssertUtils.assertLogLevel(null, Level.INFO);
        }, "null 事件应抛出异常");
    }

    @Test
    void testAssertLogLevel_WithDifferentLevels() {
        logger.trace("Trace");
        logger.debug("Debug");
        logger.info("Info");
        logger.warn("Warn");
        logger.error("Error");

        AssertUtils.assertLogLevel(appender.list.get(0), Level.TRACE);
        AssertUtils.assertLogLevel(appender.list.get(1), Level.DEBUG);
        AssertUtils.assertLogLevel(appender.list.get(2), Level.INFO);
        AssertUtils.assertLogLevel(appender.list.get(3), Level.WARN);
        AssertUtils.assertLogLevel(appender.list.get(4), Level.ERROR);
    }

    @Test
    void testAssertLogContains_Success() {
        logger.info("Test message with Status=[200]");
        ILoggingEvent event = appender.list.get(0);

        assertDoesNotThrow(() -> {
            AssertUtils.assertLogContains(event, "Status=[200]");
            AssertUtils.assertLogContains(event, "Test message");
            AssertUtils.assertLogContains(event, "Status=[200]", "自定义消息");
        });
    }

    @Test
    void testAssertLogContains_Failure() {
        logger.info("Test message");
        ILoggingEvent event = appender.list.get(0);

        assertThrows(AssertionError.class, () -> {
            AssertUtils.assertLogContains(event, "Not found");
        }, "日志不包含指定文本应抛出异常");
    }

    @Test
    void testAssertLogContains_NullEvent() {
        assertThrows(AssertionError.class, () -> {
            AssertUtils.assertLogContains(null, "test");
        }, "null 事件应抛出异常");
    }

    @Test
    void testAssertLogStatus_Success() {
        logger.info("Request completed with Status=[200]");
        ILoggingEvent event = appender.list.get(0);

        assertDoesNotThrow(() -> {
            AssertUtils.assertLogStatus(event, 200);
            AssertUtils.assertLogStatus(event, 200, "自定义消息");
        });
    }

    @Test
    void testAssertLogStatus_Failure() {
        logger.info("Request completed with Status=[200]");
        ILoggingEvent event = appender.list.get(0);

        assertThrows(AssertionError.class, () -> {
            AssertUtils.assertLogStatus(event, 404);
        }, "状态码不匹配应抛出异常");
    }

    @Test
    void testAssertLogStatus_WithDifferentStatusCodes() {
        logger.info("Status=[200]");
        logger.warn("Status=[404]");
        logger.error("Status=[500]");

        AssertUtils.assertLogStatus(appender.list.get(0), 200);
        AssertUtils.assertLogStatus(appender.list.get(1), 404);
        AssertUtils.assertLogStatus(appender.list.get(2), 500);
    }

    @Test
    void testAssertLogSize_Success() {
        logger.info("Message 1");
        logger.info("Message 2");
        logger.info("Message 3");

        assertDoesNotThrow(() -> {
            AssertUtils.assertLogSize(appender, 3);
            AssertUtils.assertLogSize(appender, 3, "自定义消息");
        });
    }

    @Test
    void testAssertLogSize_Failure() {
        logger.info("Message 1");
        logger.info("Message 2");

        assertThrows(AssertionError.class, () -> {
            AssertUtils.assertLogSize(appender, 3);
        }, "日志数量不匹配应抛出异常");
    }

    @Test
    void testAssertLogSize_NullAppender() {
        assertThrows(AssertionError.class, () -> {
            AssertUtils.assertLogSize(null, 0);
        }, "null appender 应抛出异常");
    }

    @Test
    void testAssertLogSize_EmptyList() {
        assertDoesNotThrow(() -> {
            AssertUtils.assertLogSize(appender, 0);
        });
    }
}

