package com.yggdrasil.labs.mybatis.log;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.yggdrasil.labs.test.util.LogTestUtils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.read.ListAppender;

class SlowSqlInterceptorTest {
    private Logger logger;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        logs = LogTestUtils.setupLogger("SQL.SLOW");
        logger = (Logger) LoggerFactory.getLogger("SQL.SLOW");
        logger.setLevel(Level.WARN);
    }

    @AfterEach
    void tearDown() {
        LogTestUtils.cleanupLogger("SQL.SLOW", logs);
    }

    @Test
    void thresholdIsStrictAndProceedIsCalledOnce() throws Throwable {
        long[] times = {0, 999_000_000L, 0, 1_000_000_000L, 0, 1_000_000_001L};
        AtomicInteger next = new AtomicInteger();
        SlowSqlInterceptor interceptor =
                new SlowSqlInterceptor(1000, () -> times[next.getAndIncrement()]);
        Invocation invocation = updateInvocation();
        when(invocation.proceed()).thenReturn(7);

        assertEquals(7, interceptor.intercept(invocation));
        assertEquals(7, interceptor.intercept(invocation));
        assertTrue(logs.list.isEmpty());
        assertEquals(7, interceptor.intercept(invocation));
        verify(invocation, times(3)).proceed();
        assertEquals(1, logs.list.size());
        JSONObject payload = JSON.parseObject(logs.list.get(0).getFormattedMessage());
        assertEquals("UPDATE", payload.getString("operation"));
        assertEquals("SUCCESS", payload.getString("outcome"));
        assertEquals("1000.000001", payload.getBigDecimal("elapsedMs").toPlainString());
        assertEquals("STATEMENT_CALL", payload.getString("timingScope"));
        assertEquals("<omitted>", payload.getString("sql"));
    }

    @Test
    void disabledWarnDoesNotReadClockOrSql() throws Throwable {
        logger.setLevel(Level.ERROR);
        LongSupplier clock =
                () -> {
                    throw new AssertionError("不应读取时钟");
                };
        Invocation invocation = updateInvocation();
        when(invocation.proceed()).thenReturn(9);

        assertEquals(9, new SlowSqlInterceptor(1000, clock).intercept(invocation));
        verify(invocation, times(1)).proceed();
        verify(invocation, never()).getTarget();
        assertTrue(logs.list.isEmpty());
    }

    @Test
    void disabledWarnDoesNotRetryAThrowingInvocation() throws Throwable {
        logger.setLevel(Level.ERROR);
        Invocation invocation = updateInvocation();
        IllegalStateException failure = new IllegalStateException("原异常");
        when(invocation.proceed()).thenThrow(failure);

        assertSame(
                failure,
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                new SlowSqlInterceptor(1000, System::nanoTime)
                                        .intercept(invocation)));
        verify(invocation, times(1)).proceed();
    }

    @Test
    void warnDisabledDuringExecutionDoesNotReadSql() throws Throwable {
        Invocation invocation = updateInvocation();
        when(invocation.proceed()).thenReturn(1);
        AtomicInteger calls = new AtomicInteger();
        LongSupplier clock =
                () -> {
                    if (calls.getAndIncrement() == 0) {
                        return 0;
                    }
                    logger.setLevel(Level.ERROR);
                    return 1_000_000_001L;
                };

        assertEquals(1, new SlowSqlInterceptor(1000, clock).intercept(invocation));
        verify(invocation, never()).getTarget();
        assertTrue(logs.list.isEmpty());
    }

    @Test
    void slowFailureKeepsOriginalReflectionWrapperAndDoesNotLogItsMessage() throws Throwable {
        SQLException secret = new SQLException("secret-value");
        InvocationTargetException wrapped = new InvocationTargetException(secret);
        Invocation invocation = updateInvocation();
        when(invocation.proceed()).thenThrow(wrapped);
        long[] times = {0, 1_000_000_001L};
        AtomicInteger next = new AtomicInteger();

        Throwable actual =
                assertThrows(
                        InvocationTargetException.class,
                        () ->
                                new SlowSqlInterceptor(1000, () -> times[next.getAndIncrement()])
                                        .intercept(invocation));
        assertSame(wrapped, actual);
        assertSame(secret, actual.getCause());
        assertEquals(1, logs.list.size());
        String message = logs.list.get(0).getFormattedMessage();
        assertFalse(message.contains("secret-value"));
        JSONObject payload = JSON.parseObject(message);
        assertEquals("ERROR", payload.getString("outcome"));
        assertEquals(SQLException.class.getName(), payload.getString("errorType"));
    }

    @Test
    void ordinaryLoggingFailureDoesNotChangeSqlResultOrException() throws Throwable {
        @SuppressWarnings("unchecked")
        Appender<ILoggingEvent> failing = mock(Appender.class);
        doThrow(new IllegalStateException("日志错误"), new LinkageError("后端版本冲突"))
                .when(failing)
                .doAppend(any());
        logger.addAppender(failing);
        try {
            Invocation invocation = updateInvocation();
            InvocationTargetException wrapped =
                    new InvocationTargetException(new SQLException("原异常"));
            when(invocation.proceed()).thenReturn(12).thenThrow(wrapped);
            long[] times = {0, 1_000_000_001L, 0, 1_000_000_001L};
            AtomicInteger next = new AtomicInteger();
            SlowSqlInterceptor interceptor =
                    new SlowSqlInterceptor(1000, () -> times[next.getAndIncrement()]);

            assertEquals(12, interceptor.intercept(invocation));
            assertSame(
                    wrapped,
                    assertThrows(
                            InvocationTargetException.class,
                            () -> interceptor.intercept(invocation)));
            verify(invocation, times(2)).proceed();
        } finally {
            logger.detachAppender(failing);
        }
    }

    @Test
    void fatalLoggingFailurePropagatesEvenWhenSqlHasAlreadyFailed() throws Throwable {
        VirtualMachineError fatal = new OutOfMemoryError("模拟致命错误");
        UnsynchronizedAppenderBase<ILoggingEvent> failing =
                new UnsynchronizedAppenderBase<>() {
                    @Override
                    protected void append(ILoggingEvent event) {
                        throw fatal;
                    }
                };
        failing.setContext(logger.getLoggerContext());
        failing.start();
        logger.addAppender(failing);
        Invocation invocation = updateInvocation();
        when(invocation.proceed())
                .thenThrow(new InvocationTargetException(new SQLException("原异常")));
        long[] times = {0, 1_000_000_001L};
        AtomicInteger next = new AtomicInteger();
        try {
            assertSame(
                    fatal,
                    assertThrows(
                            VirtualMachineError.class,
                            () ->
                                    new SlowSqlInterceptor(
                                                    1000, () -> times[next.getAndIncrement()])
                                            .intercept(invocation)));
        } finally {
            logger.detachAppender(failing);
            failing.stop();
        }
    }

    @Test
    void fatalMetadataGetterFailurePropagatesThroughMybatisReflection() {
        for (Error fatal : List.of(new OutOfMemoryError("模拟致命错误"), new ThreadDeath())) {
            AtomicInteger reads = new AtomicInteger();
            SlowSqlInterceptor interceptor =
                    new SlowSqlInterceptor(1, () -> reads.getAndIncrement() * 2_000_000L);
            StatementHandler handler =
                    (StatementHandler) interceptor.plugin(new MetadataHandler(fatal));

            assertSame(fatal, assertThrows(Error.class, () -> handler.update(null)));
        }
    }

    @Test
    void unicodeLineSeparatorsInStatementIdDoNotEnterTheLogMessage() throws Exception {
        Configuration configuration = new Configuration();
        for (char separator : new char[] {'\u2028', '\u2029'}) {
            MappedStatement statement =
                    new MappedStatement.Builder(
                                    configuration,
                                    "mapper" + separator + "id",
                                    new StaticSqlSource(configuration, "UPDATE t SET a=1"),
                                    SqlCommandType.UPDATE)
                            .build();
            AtomicInteger reads = new AtomicInteger();
            StatementHandler handler =
                    (StatementHandler)
                            new SlowSqlInterceptor(1, () -> reads.getAndIncrement() * 2_000_000L)
                                    .plugin(new MetadataHandler(statement));

            assertEquals(7, handler.update(null));
            String message = logs.list.get(logs.list.size() - 1).getFormattedMessage();
            assertFalse(message.contains(String.valueOf(separator)));
            assertEquals("<unknown>", JSON.parseObject(message).getString("statementId"));
        }
    }

    @Test
    void fatalCauseOfClockFailurePropagatesWithoutInvokingSql() throws Exception {
        OutOfMemoryError fatal = new OutOfMemoryError("模拟致命错误");
        Invocation invocation = updateInvocation();
        SlowSqlInterceptor interceptor =
                new SlowSqlInterceptor(
                        1,
                        () -> {
                            throw new IllegalStateException("包装异常", fatal);
                        });

        assertSame(
                fatal,
                assertThrows(OutOfMemoryError.class, () -> interceptor.intercept(invocation)));
        verify(invocation, never()).proceed();
    }

    @Test
    void cyclicOrdinaryFailureDoesNotChangeSqlResult() throws Throwable {
        IllegalStateException first = new IllegalStateException("异常一");
        IllegalStateException second = new IllegalStateException("异常二", first);
        first.initCause(second);
        Invocation invocation = updateInvocation();
        when(invocation.proceed()).thenReturn(7);
        SlowSqlInterceptor interceptor =
                new SlowSqlInterceptor(
                        1,
                        () -> {
                            throw first;
                        });

        assertEquals(7, interceptor.intercept(invocation));
        verify(invocation, times(1)).proceed();
    }

    @Test
    void failureReadingLoggingErrorCauseDoesNotReplaceSqlOutcome() throws Throwable {
        IllegalStateException unreadable =
                new IllegalStateException("日志故障") {
                    @Override
                    public synchronized Throwable getCause() {
                        throw new IllegalStateException("无法读取原因");
                    }
                };
        @SuppressWarnings("unchecked")
        Appender<ILoggingEvent> failing = mock(Appender.class);
        doThrow(unreadable).when(failing).doAppend(any());
        logger.addAppender(failing);
        try {
            Invocation invocation = updateInvocation();
            InvocationTargetException wrapped =
                    new InvocationTargetException(new SQLException("原异常"));
            when(invocation.proceed()).thenReturn(7).thenThrow(wrapped);
            AtomicInteger reads = new AtomicInteger();
            SlowSqlInterceptor interceptor =
                    new SlowSqlInterceptor(1, () -> reads.getAndIncrement() * 2_000_000L);

            assertEquals(7, interceptor.intercept(invocation));
            assertSame(
                    wrapped,
                    assertThrows(
                            InvocationTargetException.class,
                            () -> interceptor.intercept(invocation)));
        } finally {
            logger.detachAppender(failing);
        }
    }

    @Test
    void fatalErrorWhileReadingLoggingErrorCausePropagates() throws Throwable {
        OutOfMemoryError fatal = new OutOfMemoryError("模拟致命错误");
        IllegalStateException unreadable =
                new IllegalStateException("日志故障") {
                    @Override
                    public synchronized Throwable getCause() {
                        throw new IllegalStateException("包装异常", fatal);
                    }
                };
        Invocation invocation = updateInvocation();
        SlowSqlInterceptor interceptor =
                new SlowSqlInterceptor(
                        1,
                        () -> {
                            throw unreadable;
                        });

        assertSame(
                fatal,
                assertThrows(OutOfMemoryError.class, () -> interceptor.intercept(invocation)));
        verify(invocation, never()).proceed();
    }

    public static class Metadata {
        private final Error fatal;
        private final MappedStatement statement;

        Metadata(Error fatal) {
            this.fatal = fatal;
            this.statement = null;
        }

        Metadata(MappedStatement statement) {
            this.fatal = null;
            this.statement = statement;
        }

        public MappedStatement getMappedStatement() {
            return statement;
        }

        public BoundSql getBoundSql() {
            if (fatal != null) {
                throw fatal;
            }
            return null;
        }
    }

    public static class MetadataHandler implements StatementHandler {
        private final Metadata delegate;

        MetadataHandler(Error fatal) {
            this.delegate = new Metadata(fatal);
        }

        MetadataHandler(MappedStatement statement) {
            this.delegate = new Metadata(statement);
        }

        public Metadata getDelegate() {
            return delegate;
        }

        @Override
        public Statement prepare(Connection connection, Integer transactionTimeout) {
            return null;
        }

        @Override
        public void parameterize(Statement statement) {}

        @Override
        public void batch(Statement statement) {}

        @Override
        public int update(Statement statement) {
            return 7;
        }

        @Override
        public <E> List<E> query(Statement statement, ResultHandler resultHandler) {
            return List.of();
        }

        @Override
        public <E> Cursor<E> queryCursor(Statement statement) {
            return null;
        }

        @Override
        public BoundSql getBoundSql() {
            return null;
        }

        @Override
        public ParameterHandler getParameterHandler() {
            return null;
        }
    }

    private static Invocation updateInvocation() throws Exception {
        Invocation invocation = mock(Invocation.class);
        when(invocation.getMethod())
                .thenReturn(StatementHandler.class.getMethod("update", Statement.class));
        when(invocation.getTarget()).thenReturn(mock(StatementHandler.class));
        return invocation;
    }
}
