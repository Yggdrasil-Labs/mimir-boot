package com.yggdrasil.labs.test.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import org.slf4j.LoggerFactory;

/**
 * 日志测试工具类
 *
 * <p>提供日志测试的常用工具方法，简化 Logback 日志测试的设置和验证。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * // 设置日志记录器
 * ListAppender<ILoggingEvent> appender = LogTestUtils.setupLogger("access.log");
 *
 * // 验证日志
 * LogTestUtils.assertLogLevel(appender, 0, Level.INFO);
 * LogTestUtils.assertLogContains(appender, 0, "Status=[200]");
 *
 * // 清理
 * LogTestUtils.cleanupLogger(appender);
 * }</pre>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
public final class LogTestUtils {

    private static final Map<ListAppender<ILoggingEvent>, LoggerState> LOGGER_STATES =
            Collections.synchronizedMap(new IdentityHashMap<>());

    private LogTestUtils() {
        // 工具类，禁止实例化
    }

    /**
     * 设置日志记录器并返回 ListAppender
     *
     * @param loggerName 日志记录器名称
     * @return ListAppender 实例
     */
    public static ListAppender<ILoggingEvent> setupLogger(String loggerName) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = context.getLogger(loggerName);

        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        LOGGER_STATES.put(appender, new LoggerState(logger, logger.getLevel(), logger.isAdditive()));
        appender.setContext(context);
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.TRACE);
        logger.setAdditive(false);

        return appender;
    }

    /**
     * 清理日志记录器
     *
     * @param loggerName 日志记录器名称
     * @param appender   ListAppender 实例
     */
    public static void cleanupLogger(String loggerName, ListAppender<ILoggingEvent> appender) {
        if (appender == null) {
            return;
        }
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = context.getLogger(loggerName);
        cleanupLogger(logger, appender);
    }

    /**
     * 清理日志记录器（使用 Logger 实例）
     *
     * @param logger   Logger 实例
     * @param appender ListAppender 实例
     */
    public static void cleanupLogger(Logger logger, ListAppender<ILoggingEvent> appender) {
        if (appender == null) {
            return;
        }
        LoggerState state = LOGGER_STATES.remove(appender);
        if (logger != null) {
            logger.detachAppender(appender);
        }
        appender.stop();
        if (appender.list != null) {
            appender.list.clear();
        }
        if (state != null) {
            state.restore();
        }
    }

    /**
     * 验证 appender 和索引的有效性
     *
     * @param appender ListAppender 实例
     * @param index    日志索引
     * @return 日志事件
     * @throws AssertionError 如果 appender 或索引无效
     */
    private static ILoggingEvent validateAndGetEvent(ListAppender<ILoggingEvent> appender, int index) {
        if (appender == null || appender.list == null) {
            throw new AssertionError("ListAppender 或日志列表为 null");
        }
        if (index >= appender.list.size()) {
            throw new AssertionError("日志索引超出范围: " + index + " >= " + appender.list.size());
        }
        return appender.list.get(index);
    }

    /**
     * 断言日志级别
     *
     * @param appender      ListAppender 实例
     * @param index         日志索引
     * @param expectedLevel 期望的日志级别
     * @throws AssertionError 如果日志级别不匹配
     */
    public static void assertLogLevel(ListAppender<ILoggingEvent> appender, int index, Level expectedLevel) {
        AssertUtils.assertLogLevel(validateAndGetEvent(appender, index), expectedLevel);
    }

    /**
     * 断言日志包含指定文本
     *
     * @param appender     ListAppender 实例
     * @param index        日志索引
     * @param expectedText 期望包含的文本
     * @throws AssertionError 如果日志不包含指定文本
     */
    public static void assertLogContains(ListAppender<ILoggingEvent> appender, int index, String expectedText) {
        AssertUtils.assertLogContains(validateAndGetEvent(appender, index), expectedText);
    }

    /**
     * 断言日志状态码
     *
     * @param appender       ListAppender 实例
     * @param index          日志索引
     * @param expectedStatus 期望的 HTTP 状态码
     * @throws AssertionError 如果日志不包含指定状态码
     */
    public static void assertLogStatus(ListAppender<ILoggingEvent> appender, int index, int expectedStatus) {
        assertLogContains(appender, index, "Status=[" + expectedStatus + "]");
    }

    /**
     * 获取日志事件
     *
     * @param appender ListAppender 实例
     * @param index    日志索引
     * @return 日志事件
     * @throws AssertionError 如果索引超出范围
     */
    public static ILoggingEvent getLogEvent(ListAppender<ILoggingEvent> appender, int index) {
        return validateAndGetEvent(appender, index);
    }

    /**
     * 获取已捕获的日志数量。
     *
     * <p>ListAppender 的事件列表访问集中在此 Logback 适配边界，通用断言工具不依赖其内部字段。</p>
     *
     * @param appender ListAppender 实例
     * @return 已捕获的日志数量
     */
    public static int getLogSize(ListAppender<ILoggingEvent> appender) {
        if (appender == null || appender.list == null) {
            throw new AssertionError("ListAppender 或日志列表为 null");
        }
        return appender.list.size();
    }

    private record LoggerState(Logger logger, Level level, boolean additive) {

        private void restore() {
            logger.setLevel(level);
            logger.setAdditive(additive);
        }
    }
}
