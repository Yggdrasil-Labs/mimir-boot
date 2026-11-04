package com.yggdrasil.labs.mybatis.log;

import java.math.BigDecimal;
import java.sql.Statement;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.ExceptionUtil;
import org.apache.ibatis.session.ResultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;

/** 记录超过阈值的语句调用。 */
@Intercepts({
    @Signature(
            type = StatementHandler.class,
            method = "query",
            args = {Statement.class, ResultHandler.class}),
    @Signature(
            type = StatementHandler.class,
            method = "update",
            args = {Statement.class}),
    @Signature(
            type = StatementHandler.class,
            method = "queryCursor",
            args = {Statement.class})
})
public class SlowSqlInterceptor implements Interceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger("SQL.SLOW");
    private static final String UNKNOWN = "<unknown>";
    private final long thresholdMs;
    private final long thresholdNanos;
    private final LongSupplier clock;

    public SlowSqlInterceptor(long thresholdMs) {
        this(thresholdMs, System::nanoTime);
    }

    SlowSqlInterceptor(long thresholdMs, LongSupplier clock) {
        if (thresholdMs < 1 || thresholdMs > Long.MAX_VALUE / 1_000_000) {
            throw new IllegalArgumentException("thresholdMs 超出有效范围");
        }
        this.thresholdMs = thresholdMs;
        this.thresholdNanos = thresholdMs * 1_000_000;
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        boolean enabled;
        try {
            enabled = LOGGER.isWarnEnabled();
        } catch (RuntimeException | LinkageError error) {
            rethrowFatal(error);
            enabled = false;
        }
        if (!enabled) {
            return invocation.proceed();
        }
        long start;
        try {
            start = clock.getAsLong();
        } catch (RuntimeException | LinkageError error) {
            rethrowFatal(error);
            return invocation.proceed();
        }

        Throwable failure = null;
        try {
            return invocation.proceed();
        } catch (Throwable error) {
            failure = error;
            throw error;
        } finally {
            try {
                long elapsed = clock.getAsLong() - start;
                if (elapsed > thresholdNanos && LOGGER.isWarnEnabled()) {
                    logSlowCall(invocation, elapsed, failure);
                }
            } catch (Exception | LinkageError error) {
                rethrowFatal(error);
                // 观测失败不能改变数据库调用的结果。
            }
        }
    }

    private void logSlowCall(Invocation invocation, long elapsed, Throwable failure) {
        String statementId = UNKNOWN;
        String sql = null;
        try {
            PluginUtils.MPStatementHandler handler =
                    PluginUtils.mpStatementHandler((StatementHandler) invocation.getTarget());
            try {
                MappedStatement mappedStatement = handler.mappedStatement();
                if (mappedStatement != null) {
                    statementId = safeStatementId(mappedStatement.getId());
                }
            } catch (Exception | LinkageError error) {
                rethrowFatal(error);
                // 保留安全占位符并继续获取 SQL 模板。
            }
            try {
                BoundSql boundSql = handler.boundSql();
                if (boundSql != null) {
                    sql = boundSql.getSql();
                }
            } catch (Exception | LinkageError error) {
                rethrowFatal(error);
                // 元数据不可用时输出省略标记。
            }
        } catch (Exception | LinkageError error) {
            rethrowFatal(error);
            // 非标准代理可能无法提取元数据，仍保留耗时与结果。
        }

        SlowSqlTextSanitizer.Result result = SlowSqlTextSanitizer.sanitize(sql);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("statementId", statementId);
        payload.put("operation", operation(invocation.getMethod().getName()));
        payload.put("elapsedMs", BigDecimal.valueOf(elapsed, 6));
        payload.put("thresholdMs", thresholdMs);
        payload.put("timingScope", "STATEMENT_CALL");
        payload.put("outcome", failure == null ? "SUCCESS" : "ERROR");
        payload.put(
                "errorType",
                failure == null
                        ? null
                        : ExceptionUtil.unwrapThrowable(failure).getClass().getName());
        payload.put("sql", result.sql());
        payload.put("sqlStatus", result.status());
        LOGGER.warn(JSON.toJSONString(payload, JSONWriter.Feature.WriteNulls));
    }

    private static String operation(String method) {
        return switch (method) {
            case "query" -> "QUERY";
            case "update" -> "UPDATE";
            case "queryCursor" -> "CURSOR_OPEN";
            default -> throw new IllegalArgumentException("不支持的语句调用");
        };
    }

    private static void rethrowFatal(Throwable error) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable cause = error;
        while (cause != null && visited.add(cause)) {
            if (cause instanceof VirtualMachineError fatal) {
                throw fatal;
            }
            if (cause instanceof ThreadDeath fatal) {
                throw fatal;
            }
            try {
                cause = cause.getCause();
            } catch (RuntimeException | LinkageError unreadableCause) {
                cause = unreadableCause;
            }
        }
    }

    private static String safeStatementId(String id) {
        if (id == null || id.length() > 512) {
            return UNKNOWN;
        }
        for (int i = 0; i < id.length(); i++) {
            char current = id.charAt(i);
            if (Character.isISOControl(current) || current == '\u2028' || current == '\u2029') {
                return UNKNOWN;
            }
        }
        return id;
    }
}
