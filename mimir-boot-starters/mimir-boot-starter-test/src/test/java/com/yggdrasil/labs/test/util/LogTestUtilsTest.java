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

import static org.junit.jupiter.api.Assertions.*;

/**
 * LogTestUtils 测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class LogTestUtilsTest {

    private static final String TEST_LOGGER_NAME = "test.logger";
    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        appender = LogTestUtils.setupLogger(TEST_LOGGER_NAME);
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        logger = context.getLogger(TEST_LOGGER_NAME);
    }

    @AfterEach
    void tearDown() {
        LogTestUtils.cleanupLogger(logger, appender);
    }

    @Test
    void testSetupLogger() {
        assertNotNull(appender, "ListAppender 不应为 null");
        assertNotNull(logger, "Logger 不应为 null");
        assertEquals(Level.TRACE, logger.getLevel(), "日志级别应为 TRACE");
        assertFalse(logger.isAdditive(), "Logger 不应是 additive");
    }

    @Test
    void testCleanupLogger_WithLoggerName() {
        ListAppender<ILoggingEvent> testAppender = LogTestUtils.setupLogger("test.cleanup");
        assertNotNull(testAppender);

        LogTestUtils.cleanupLogger("test.cleanup", testAppender);

        // 验证清理后可以重新设置
        ListAppender<ILoggingEvent> newAppender = LogTestUtils.setupLogger("test.cleanup");
        assertNotNull(newAppender);
        LogTestUtils.cleanupLogger("test.cleanup", newAppender);
    }

    @Test
    void testCleanupLogger_WithNullAppender() {
        // 不应抛出异常
        assertDoesNotThrow(() -> {
            LogTestUtils.cleanupLogger(logger, null);
            LogTestUtils.cleanupLogger("test.logger", null);
        });
    }

    @Test
    void testCleanupLogger_WithNullLogger() {
        ListAppender<ILoggingEvent> testAppender = LogTestUtils.setupLogger("test.null.logger");
        assertDoesNotThrow(() -> {
            LogTestUtils.cleanupLogger((Logger) null, testAppender);
        });
        LogTestUtils.cleanupLogger("test.null.logger", testAppender);
    }

    @Test
    void testAssertLogLevel_Success() {
        logger.info("Test message");

        assertDoesNotThrow(() -> {
            LogTestUtils.assertLogLevel(appender, 0, Level.INFO);
        });
    }

    @Test
    void testAssertLogLevel_Failure() {
        logger.info("Test message");

        assertThrows(AssertionError.class, () -> {
            LogTestUtils.assertLogLevel(appender, 0, Level.ERROR);
        }, "日志级别不匹配应抛出异常");
    }

    @Test
    void testAssertLogLevel_WithDifferentLevels() {
        logger.trace("Trace message");
        logger.debug("Debug message");
        logger.info("Info message");
        logger.warn("Warn message");
        logger.error("Error message");

        LogTestUtils.assertLogLevel(appender, 0, Level.TRACE);
        LogTestUtils.assertLogLevel(appender, 1, Level.DEBUG);
        LogTestUtils.assertLogLevel(appender, 2, Level.INFO);
        LogTestUtils.assertLogLevel(appender, 3, Level.WARN);
        LogTestUtils.assertLogLevel(appender, 4, Level.ERROR);
    }

    @Test
    void testAssertLogLevel_NullAppender() {
        assertThrows(AssertionError.class, () -> {
            LogTestUtils.assertLogLevel(null, 0, Level.INFO);
        }, "null appender 应抛出异常");
    }

    @Test
    void testAssertLogLevel_IndexOutOfRange() {
        logger.info("Test message");

        assertThrows(AssertionError.class, () -> {
            LogTestUtils.assertLogLevel(appender, 1, Level.INFO);
        }, "索引超出范围应抛出异常");
    }

    @Test
    void testAssertLogContains_Success() {
        logger.info("Test message with Status=[200]");

        assertDoesNotThrow(() -> {
            LogTestUtils.assertLogContains(appender, 0, "Status=[200]");
            LogTestUtils.assertLogContains(appender, 0, "Test message");
        });
    }

    @Test
    void testAssertLogContains_Failure() {
        logger.info("Test message");

        assertThrows(AssertionError.class, () -> {
            LogTestUtils.assertLogContains(appender, 0, "Not found");
        }, "日志不包含指定文本应抛出异常");
    }

    @Test
    void testAssertLogContains_NullAppender() {
        assertThrows(AssertionError.class, () -> {
            LogTestUtils.assertLogContains(null, 0, "test");
        }, "null appender 应抛出异常");
    }

    @Test
    void testAssertLogContains_IndexOutOfRange() {
        logger.info("Test message");

        assertThrows(AssertionError.class, () -> {
            LogTestUtils.assertLogContains(appender, 1, "test");
        }, "索引超出范围应抛出异常");
    }

    @Test
    void testAssertLogStatus_Success() {
        logger.info("Request completed with Status=[200]");

        assertDoesNotThrow(() -> {
            LogTestUtils.assertLogStatus(appender, 0, 200);
        });
    }

    @Test
    void testAssertLogStatus_Failure() {
        logger.info("Request completed with Status=[200]");

        assertThrows(AssertionError.class, () -> {
            LogTestUtils.assertLogStatus(appender, 0, 404);
        }, "状态码不匹配应抛出异常");
    }

    @Test
    void testAssertLogStatus_WithDifferentStatusCodes() {
        logger.info("Status=[200]");
        logger.warn("Status=[404]");
        logger.error("Status=[500]");

        LogTestUtils.assertLogStatus(appender, 0, 200);
        LogTestUtils.assertLogStatus(appender, 1, 404);
        LogTestUtils.assertLogStatus(appender, 2, 500);
    }

    @Test
    void testGetLogEvent_Success() {
        logger.info("Test message");

        ILoggingEvent event = LogTestUtils.getLogEvent(appender, 0);

        assertNotNull(event, "日志事件不应为 null");
        assertEquals(Level.INFO, event.getLevel());
        assertTrue(event.getFormattedMessage().contains("Test message"));
    }

    @Test
    void testGetLogEvent_NullAppender() {
        assertThrows(AssertionError.class, () -> {
            LogTestUtils.getLogEvent(null, 0);
        }, "null appender 应抛出异常");
    }

    @Test
    void testGetLogEvent_IndexOutOfRange() {
        logger.info("Test message");

        assertThrows(AssertionError.class, () -> {
            LogTestUtils.getLogEvent(appender, 1);
        }, "索引超出范围应抛出异常");
    }

    @Test
    void testMultipleLogs() {
        logger.info("First message");
        logger.warn("Second message");
        logger.error("Third message");

        assertEquals(3, appender.list.size(), "应有 3 条日志");

        LogTestUtils.assertLogLevel(appender, 0, Level.INFO);
        LogTestUtils.assertLogContains(appender, 0, "First");

        LogTestUtils.assertLogLevel(appender, 1, Level.WARN);
        LogTestUtils.assertLogContains(appender, 1, "Second");

        LogTestUtils.assertLogLevel(appender, 2, Level.ERROR);
        LogTestUtils.assertLogContains(appender, 2, "Third");
    }
}

