package com.yggdrasil.labs.mybatis.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.yggdrasil.labs.test.base.BaseUnitTest;
import com.yggdrasil.labs.test.util.LogTestUtils;
import org.apache.ibatis.binding.MapperMethod;
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
class JsonSqlLogInnerInterceptorTest extends BaseUnitTest {

    private JsonSqlLogInnerInterceptor interceptor;
    private ListAppender<ILoggingEvent> listAppender;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();
        interceptor = new JsonSqlLogInnerInterceptor();
        
        // 配置日志捕获
        listAppender = LogTestUtils.setupLogger("SQL.JSON");
        Logger logger = (Logger) LoggerFactory.getLogger("SQL.JSON");
        logger.setLevel(Level.INFO);
    }

    @Override
    @AfterEach
    protected void tearDown() {
        LogTestUtils.cleanupLogger("SQL.JSON", listAppender);
        super.tearDown();
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
    void shouldMaskSensitiveScalarMapParametersInLogs() {
        StatementHandler statementHandler = mock(StatementHandler.class);
        BoundSql boundSql = mock(BoundSql.class);
        Connection connection = mock(Connection.class);
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("password", "plain-password");
        params.put("token", "plain-token");
        params.put("secret", "plain-secret");
        params.put("authorization", "Bearer plain-authorization");
        params.put("access_token", "plain-access-token");
        params.put("name", "safe-name");

        when(statementHandler.getBoundSql()).thenReturn(boundSql);
        when(boundSql.getSql()).thenReturn("SELECT * FROM user WHERE password = ? AND token = ?");
        when(boundSql.getParameterObject()).thenReturn(params);

        interceptor.beforePrepare(statementHandler, connection, null);

        String message = listAppender.list.get(0).getMessage();
        assertFalse(message.contains("plain-password"));
        assertFalse(message.contains("plain-token"));
        assertFalse(message.contains("plain-secret"));
        assertFalse(message.contains("plain-authorization"));
        assertFalse(message.contains("plain-access-token"));
        assertTrue(message.contains("safe-name"));
    }

    @Test
    void shouldMaskMybatisParameterAliasesForSensitiveValues() {
        StatementHandler statementHandler = mock(StatementHandler.class);
        BoundSql boundSql = mock(BoundSql.class);
        Connection connection = mock(Connection.class);
        MapperMethod.ParamMap<Object> params = new MapperMethod.ParamMap<>();
        String password = "plain-password";
        params.put("password", password);
        params.put("param1", password);
        params.put("name", "safe-name");
        params.put("param2", "safe-name");

        when(statementHandler.getBoundSql()).thenReturn(boundSql);
        when(boundSql.getSql()).thenReturn("SELECT * FROM user WHERE password = ? AND name = ?");
        when(boundSql.getParameterObject()).thenReturn(params);

        interceptor.beforePrepare(statementHandler, connection, null);

        String message = listAppender.list.get(0).getMessage();
        assertFalse(message.contains("plain-password"));
        assertTrue(message.contains("safe-name"));
    }

    @Test
    void shouldMaskSensitiveValuesInBothSqlTextAndParameters() {
        StatementHandler statementHandler = mock(StatementHandler.class);
        BoundSql boundSql = mock(BoundSql.class);
        Connection connection = mock(Connection.class);
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("secret", "parameter-secret");

        when(statementHandler.getBoundSql()).thenReturn(boundSql);
        when(boundSql.getSql()).thenReturn("SELECT * FROM account WHERE password = 'sql-secret'");
        when(boundSql.getParameterObject()).thenReturn(params);

        interceptor.beforePrepare(statementHandler, connection, null);

        String message = listAppender.list.get(0).getMessage();
        assertFalse(message.contains("sql-secret"));
        assertFalse(message.contains("parameter-secret"));
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
