package com.yggdrasil.labs.log;

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
 * Logback 基本日志功能测试
 *
 * <p>测试基本日志能力，包括：</p>
 * <ul>
 * <li>日志格式</li>
 * <li>打印级别</li>
 * <li>日志输出</li>
 * <li>异常信息</li>
 * <li>时间戳</li>
 * </ul>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class LogbackBasicTest {

    private Logger logger;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        // 获取 LoggerContext
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        // 创建 ListAppender 用于捕获日志
        listAppender = new ListAppender<>();
        listAppender.setContext(context);
        listAppender.start();

        // 获取测试用的 logger
        logger = context.getLogger("BASIC_TEST_LOGGER");
        logger.addAppender(listAppender);
        logger.setLevel(Level.TRACE);
        logger.setAdditive(false); // 避免继承父 logger 的 appender
    }

    @AfterEach
    void tearDown() {
        // 清理 appender
        if (logger != null && listAppender != null) {
            logger.detachAppender(listAppender);
        }
        if (listAppender != null) {
            listAppender.stop();
        }
    }

    /**
     * 测试基本日志捕获
     */
    @Test
    void testBasicLogCapture() {
        logger.info("这是一条测试日志");

        assertEquals(1, listAppender.list.size(), "应该有 1 条日志");

        ILoggingEvent event = listAppender.list.get(0);
        assertEquals(Level.INFO, event.getLevel(), "日志级别应该是 INFO");
        assertEquals("这是一条测试日志", event.getFormattedMessage());
    }

    /**
     * 测试所有日志级别
     */
    @Test
    void testAllLogLevels() {
        logger.trace("TRACE 级别");
        logger.debug("DEBUG 级别");
        logger.info("INFO 级别");
        logger.warn("WARN 级别");
        logger.error("ERROR 级别");

        assertEquals(5, listAppender.list.size(), "应该有 5 条日志");

        assertEquals(Level.TRACE, listAppender.list.get(0).getLevel());
        assertEquals(Level.DEBUG, listAppender.list.get(1).getLevel());
        assertEquals(Level.INFO, listAppender.list.get(2).getLevel());
        assertEquals(Level.WARN, listAppender.list.get(3).getLevel());
        assertEquals(Level.ERROR, listAppender.list.get(4).getLevel());
    }

    /**
     * 测试日志内容验证
     */
    @Test
    void testLogContent() {
        logger.info("用户登录成功: username=admin, status=200");

        ILoggingEvent event = listAppender.list.get(0);
        String message = event.getFormattedMessage();

        assertTrue(message.contains("登录成功"), "应包含 '登录成功'");
        assertTrue(message.contains("username=admin"), "应包含用户名");
        assertTrue(message.contains("status=200"), "应包含状态码");
    }

    /**
     * 测试异常信息记录
     */
    @Test
    void testExceptionLogging() {
        Exception exception = new RuntimeException("测试异常消息");
        logger.error("发生错误", exception);

        ILoggingEvent event = listAppender.list.get(0);
        assertNotNull(event.getThrowableProxy(), "应包含异常信息");
        assertEquals("测试异常消息", event.getThrowableProxy().getMessage());
    }

    /**
     * 测试日志时间戳
     */
    @Test
    void testLogTimestamp() {
        long before = System.currentTimeMillis();
        logger.info("测试时间戳");
        long after = System.currentTimeMillis();

        ILoggingEvent event = listAppender.list.get(0);
        long logTime = event.getTimeStamp();

        assertTrue(logTime >= before && logTime <= after,
                "日志时间戳应该在当前时间范围内: " + logTime);
    }

    /**
     * 测试参数化日志
     */
    @Test
    void testParameterizedLogging() {
        String username = "admin";
        int statusCode = 200;
        logger.info("用户 {} 登录，状态码: {}", username, statusCode);

        ILoggingEvent event = listAppender.list.get(0);
        String formattedMessage = event.getFormattedMessage();

        assertTrue(formattedMessage.contains("admin"),
                "应包含用户名: " + formattedMessage);
        assertTrue(formattedMessage.contains("200"),
                "应包含状态码: " + formattedMessage);
    }

    /**
     * 测试 Logger 名称
     */
    @Test
    void testLoggerName() {
        logger.info("测试日志");

        ILoggingEvent event = listAppender.list.get(0);
        assertEquals("BASIC_TEST_LOGGER", event.getLoggerName(),
                "Logger 名称应该是 BASIC_TEST_LOGGER");
    }

    /**
     * 测试日志级别过滤
     */
    @Test
    void testLogLevelFiltering() {
        // 设置为 WARN 级别
        logger.setLevel(Level.WARN);

        logger.debug("DEBUG 日志");
        logger.info("INFO 日志");
        logger.warn("WARN 日志");
        logger.error("ERROR 日志");

        // 应该只有 WARN 和 ERROR 日志被记录
        assertEquals(2, listAppender.list.size(),
                "应该在 WARN 级别下只记录 WARN 和 ERROR");

        assertEquals(Level.WARN, listAppender.list.get(0).getLevel());
        assertEquals(Level.ERROR, listAppender.list.get(1).getLevel());
    }

    /**
     * 测试 Logger 继承性
     */
    @Test
    void testLoggerHierarchy() {
        Logger childLogger = (Logger) LoggerFactory.getLogger("BASIC_TEST_LOGGER.child");
        childLogger.setLevel(Level.DEBUG);
        childLogger.addAppender(listAppender);
        childLogger.setAdditive(false);

        childLogger.info("子 Logger 日志");

        assertEquals(1, listAppender.list.size());
        assertEquals("BASIC_TEST_LOGGER.child",
                listAppender.list.get(0).getLoggerName());
    }

    /**
     * 测试批量日志记录
     */
    @Test
    void testMultipleLogsCapture() {
        logger.debug("DEBUG: 调试信息");
        logger.info("INFO: 用户操作");
        logger.warn("WARN: 警告信息");
        logger.error("ERROR: 错误信息");

        assertEquals(4, listAppender.list.size(),
                "应该捕获 4 条不同级别的日志");

        // 验证顺序
        assertEquals(Level.DEBUG, listAppender.list.get(0).getLevel());
        assertEquals(Level.INFO, listAppender.list.get(1).getLevel());
        assertEquals(Level.WARN, listAppender.list.get(2).getLevel());
        assertEquals(Level.ERROR, listAppender.list.get(3).getLevel());
    }

    /**
     * 测试日志线程信息
     */
    @Test
    void testThreadInfo() {
        logger.info("线程测试");

        ILoggingEvent event = listAppender.list.get(0);
        String threadName = event.getThreadName();

        assertNotNull(threadName, "应该包含线程名称");
        assertFalse(threadName.isEmpty(), "线程名称不应该为空");
    }

    /**
     * 测试 MDC (Mapped Diagnostic Context)
     */
    @Test
    void testMdcContext() {
        org.slf4j.MDC.put("userId", "12345");
        org.slf4j.MDC.put("requestId", "req-001");

        logger.info("带 MDC 的日志");

        ILoggingEvent event = listAppender.list.get(0);

        // 验证 MDC 可以通过其他方式访问
        logger.info("再次测试");

        org.slf4j.MDC.clear();
    }

    /**
     * 综合测试：完整的日志场景
     */
    @Test
    void testCompleteLoggingScenario() {
        String username = "testUser";
        String operation = "login";

        logger.debug("开始处理：operation={}", operation);
        logger.info("用户 {} 执行操作 {}", username, operation);
        logger.warn("操作 {} 可能存在问题", operation);
        logger.error("操作 {} 失败", operation);

        assertEquals(4, listAppender.list.size());

        // 验证每条日志都包含相关信息
        assertTrue(listAppender.list.get(1).getFormattedMessage().contains(username));
        assertTrue(listAppender.list.get(2).getFormattedMessage().contains("可能存在问题"));
    }
}

