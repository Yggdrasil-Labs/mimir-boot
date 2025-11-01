package com.yggdrasil.labs.mybatis.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * JSON SQL 日志拦截器测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class JsonSqlLogInnerInterceptorTest {

    private JsonSqlLogInnerInterceptor interceptor;
    private Logger logger;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        interceptor = new JsonSqlLogInnerInterceptor();
        
        // 配置日志捕获
        logger = (Logger) LoggerFactory.getLogger("SQL.JSON");
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
        logger.setLevel(Level.INFO);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(listAppender);
    }

    @Test
    void testBeforePrepare() {
        StatementHandler statementHandler = mock(StatementHandler.class);
        BoundSql boundSql = mock(BoundSql.class);
        Connection connection = mock(Connection.class);

        when(statementHandler.getBoundSql()).thenReturn(boundSql);
        when(boundSql.getSql()).thenReturn("SELECT * FROM user WHERE id = ?");
        when(boundSql.getParameterObject()).thenReturn(null);

        assertDoesNotThrow(() -> {
            interceptor.beforePrepare(statementHandler, connection, null);
        });

        // 验证日志输出
        List<ILoggingEvent> logs = listAppender.list;
        assertFalse(logs.isEmpty());
        assertTrue(logs.get(0).getMessage().contains("sql"));
    }

    @Test
    void testBeforePrepareWithParameters() {
        StatementHandler statementHandler = mock(StatementHandler.class);
        BoundSql boundSql = mock(BoundSql.class);
        Connection connection = mock(Connection.class);
        
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("id", 123);
        params.put("name", "test");

        when(statementHandler.getBoundSql()).thenReturn(boundSql);
        when(boundSql.getSql()).thenReturn("SELECT * FROM user WHERE id = ? AND name = ?");
        when(boundSql.getParameterObject()).thenReturn(params);

        assertDoesNotThrow(() -> {
            interceptor.beforePrepare(statementHandler, connection, null);
        });

        // 验证日志包含参数
        List<ILoggingEvent> logs = listAppender.list;
        assertFalse(logs.isEmpty());
        String message = logs.get(0).getMessage();
        assertTrue(message.contains("sql"));
        assertTrue(message.contains("params"));
    }

    @Test
    void testBeforePrepareWithException() {
        StatementHandler statementHandler = mock(StatementHandler.class);
        Connection connection = mock(Connection.class);

        when(statementHandler.getBoundSql()).thenThrow(new RuntimeException("Test exception"));

        // 应该捕获异常，不抛出
        assertDoesNotThrow(() -> {
            interceptor.beforePrepare(statementHandler, connection, null);
        });
    }

    @Test
    void testBeforePrepareWithNullBoundSql() {
        StatementHandler statementHandler = mock(StatementHandler.class);
        Connection connection = mock(Connection.class);

        when(statementHandler.getBoundSql()).thenReturn(null);

        assertDoesNotThrow(() -> {
            interceptor.beforePrepare(statementHandler, connection, null);
        });
    }
}

