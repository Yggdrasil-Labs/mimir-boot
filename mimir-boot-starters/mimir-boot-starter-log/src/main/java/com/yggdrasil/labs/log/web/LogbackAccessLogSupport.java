package com.yggdrasil.labs.log.web;

import org.slf4j.Logger;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;
import org.slf4j.spi.NOPLoggingEventBuilder;

import ch.qos.logback.core.spi.FilterReply;

/** 隔离可选 Logback 类型，并为访问日志保留传统 API 的完整过滤语义。 */
final class LogbackAccessLogSupport {

    private LogbackAccessLogSupport() {}

    static LoggingEventBuilder makeBuilder(
            Logger logger, Level level, String message, Object[] arguments) {
        if (!(logger instanceof ch.qos.logback.classic.Logger backend)) {
            return logger.atLevel(level);
        }
        ch.qos.logback.classic.Level backendLevel =
                ch.qos.logback.classic.Level.convertAnSLF4JLevel(level);
        FilterReply decision =
                backend.getLoggerContext()
                        .getTurboFilterList()
                        .getTurboFilterChainDecision(
                                null, backend, backendLevel, message, arguments, null);
        if (decision == FilterReply.DENY
                || (decision == FilterReply.NEUTRAL
                        && backend.getEffectiveLevel().toInt() > backendLevel.toInt())) {
            return NOPLoggingEventBuilder.singleton();
        }
        // 已使用完整模板及参数判定一次；直接建 builder，避免空消息复查改变 ACCEPT 或有状态过滤器的结果。
        return logger.makeLoggingEventBuilder(level);
    }
}
