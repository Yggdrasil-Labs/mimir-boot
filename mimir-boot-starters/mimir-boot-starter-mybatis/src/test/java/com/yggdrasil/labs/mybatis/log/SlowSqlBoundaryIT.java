package com.yggdrasil.labs.mybatis.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

/** 验证慢 SQL 日志在真实 MyBatis 并发调用中的计时隔离与参数保护。 */
class SlowSqlBoundaryIT {
    private static final String SECRET_PREFIX = "concurrency-secret-";
    private static final String UNUSED_STATEMENT = PoisonMapper.class.getName() + ".readConstant";

    private Logger logger;
    private Level previousLevel;
    private boolean previousAdditive;
    private SnapshotAppender appender;

    @BeforeEach
    void captureLogs() {
        logger = (Logger) LoggerFactory.getLogger("SQL.SLOW");
        previousLevel = logger.getLevel();
        previousAdditive = logger.isAdditive();
        appender = new SnapshotAppender();
        appender.setContext(logger.getLoggerContext());
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.WARN);
        logger.setAdditive(false);
    }

    @AfterEach
    void restoreLogger() {
        if (logger != null) {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            logger.setAdditive(previousAdditive);
        }
        if (appender != null) {
            appender.stop();
        }
    }

    @Test
    void realMyBatisCallsShareInterceptorAcrossIndependentSessions() throws Exception {
        int threadCount = 4;
        int callsPerThread = 4;
        CyclicBarrier starts = new CyclicBarrier(threadCount);
        ThreadLocal<long[]> ticks = ThreadLocal.withInitial(() -> new long[1]);
        SlowSqlInterceptor interceptor =
                new SlowSqlInterceptor(
                        1,
                        () -> {
                            long[] tick = ticks.get();
                            if (tick[0] == 0) {
                                await(starts);
                                tick[0] = 1;
                                return Thread.currentThread().getId() * 1_000_000_000L;
                            }
                            tick[0] = 0;
                            return Thread.currentThread().getId() * 1_000_000_000L
                                    + expectedElapsedMs(Thread.currentThread().getName())
                                            * 1_000_000L;
                        });
        SqlSessionFactory factory = factory(interceptor, "concurrent");
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        try {
            List<Callable<List<String>>> jobs = new ArrayList<>();
            for (int worker = 0; worker < threadCount; worker++) {
                final int workerId = worker;
                jobs.add(
                        () -> {
                            List<String> results = new ArrayList<>();
                            try (SqlSession session = factory.openSession()) {
                                ConcurrentMapper mapper = session.getMapper(ConcurrentMapper.class);
                                for (int call = 0; call < callsPerThread; call++) {
                                    String secret = SECRET_PREFIX + workerId + "-" + call;
                                    results.add(
                                            call % 2 == 0
                                                    ? mapper.readA(secret)
                                                    : mapper.readB(secret));
                                }
                            }
                            return results;
                        });
            }

            List<Future<List<String>>> futures = pool.invokeAll(jobs, 20, TimeUnit.SECONDS);
            for (int worker = 0; worker < futures.size(); worker++) {
                List<String> actual = futures.get(worker).get(20, TimeUnit.SECONDS);
                List<String> expected = new ArrayList<>();
                for (int call = 0; call < callsPerThread; call++) {
                    expected.add(SECRET_PREFIX + worker + "-" + call);
                }
                assertEquals(expected, actual);
            }
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }

        List<Snapshot> events = appender.snapshots();
        assertEquals(threadCount * callsPerThread, events.size());
        for (Snapshot event : events) {
            String message = event.message();
            assertTrue(message.contains("\"outcome\":\"SUCCESS\""), message);
            String elapsed =
                    String.format("\"elapsedMs\":%d.000000", expectedElapsedMs(event.threadName()));
            assertTrue(message.contains(elapsed), message);
            assertTrue(
                    message.contains(
                                    "\"statementId\":\""
                                            + ConcurrentMapper.class.getName()
                                            + ".readA\"")
                            || message.contains(
                                    "\"statementId\":\""
                                            + ConcurrentMapper.class.getName()
                                            + ".readB\""),
                    message);
            assertFalse(message.contains(SECRET_PREFIX), message);
            assertTrue(event.threadName().startsWith("pool-"), event.threadName());
        }
        for (int threadNumber = 1; threadNumber <= threadCount; threadNumber++) {
            String threadName = "pool-" + poolNumber(events) + "-thread-" + threadNumber;
            List<Snapshot> workerEvents =
                    events.stream().filter(e -> e.threadName().equals(threadName)).toList();
            assertEquals(callsPerThread, workerEvents.size(), threadName);
            assertEquals(
                    callsPerThread / 2,
                    workerEvents.stream().filter(e -> e.message().contains(".readA\"")).count(),
                    threadName);
            assertEquals(
                    callsPerThread / 2,
                    workerEvents.stream().filter(e -> e.message().contains(".readB\"")).count(),
                    threadName);
            for (Snapshot event : workerEvents) {
                assertTrue(
                        event.message()
                                .contains(
                                        "\"elapsedMs\":"
                                                + expectedElapsedMs(threadName)
                                                + ".000000"),
                        event.message());
            }
        }
    }

    @Test
    void unusedPoisonParameterIsNotInspectedByRealMapperExecution() {
        SqlSessionFactory factory = factory(new SlowSqlInterceptor(1, new FixedClock()), "poison");
        PoisonParameter parameter = new PoisonParameter();
        try (SqlSession session = factory.openSession()) {
            assertEquals(1, session.getMapper(PoisonMapper.class).readConstant(parameter));
        }

        List<Snapshot> events = appender.snapshots();
        assertEquals(1, events.size());
        assertTrue(
                events.get(0).message().contains("\"statementId\":\"" + UNUSED_STATEMENT + "\""));
        assertTrue(events.get(0).message().contains("\"elapsedMs\":3.500000"));
        assertTrue(events.get(0).message().contains("\"outcome\":\"SUCCESS\""));
        assertEquals(0, parameter.getterCalls);
        assertEquals(0, parameter.toStringCalls);
    }

    private static SqlSessionFactory factory(Interceptor interceptor, String databaseName) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1");
        Configuration configuration =
                new Configuration(
                        new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.addInterceptor(interceptor);
        configuration.addMapper(ConcurrentMapper.class);
        configuration.addMapper(PoisonMapper.class);
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception error) {
            throw new IllegalStateException("MyBatis 工作线程屏障等待失败", error);
        }
    }

    private static int expectedElapsedMs(String threadName) {
        int marker = threadName.lastIndexOf("-thread-");
        if (marker < 0) {
            throw new IllegalArgumentException("无法从工作线程名读取编号: " + threadName);
        }
        return Integer.parseInt(threadName.substring(marker + "-thread-".length())) + 1;
    }

    private static String poolNumber(List<Snapshot> events) {
        String threadName = events.get(0).threadName();
        int marker = threadName.lastIndexOf("-thread-");
        if (!threadName.startsWith("pool-") || marker < 0) {
            throw new IllegalArgumentException("无法从工作线程名读取线程池编号: " + threadName);
        }
        return threadName.substring("pool-".length(), marker);
    }

    interface ConcurrentMapper {
        @Select("SELECT #{value}")
        String readA(@Param("value") String value);

        @Select("SELECT CAST(#{value} AS VARCHAR)")
        String readB(@Param("value") String value);
    }

    interface PoisonMapper {
        @Select("SELECT 1")
        int readConstant(PoisonParameter ignored);
    }

    static final class PoisonParameter {
        int getterCalls;
        int toStringCalls;

        public String getSecret() {
            getterCalls++;
            throw new AssertionError("getter must not be read");
        }

        @Override
        public String toString() {
            toStringCalls++;
            throw new AssertionError("toString must not be called");
        }
    }

    private static final class FixedClock implements java.util.function.LongSupplier {
        private final ThreadLocal<Integer> calls = ThreadLocal.withInitial(() -> 0);

        @Override
        public long getAsLong() {
            int call = calls.get();
            calls.set(call + 1);
            return call % 2 == 0 ? 0L : 3_500_000L;
        }
    }

    private record Snapshot(String message, String threadName) {}

    private static final class SnapshotAppender extends AppenderBase<ILoggingEvent> {
        private final List<Snapshot> events = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        protected void append(ILoggingEvent event) {
            events.add(new Snapshot(event.getFormattedMessage(), event.getThreadName()));
        }

        List<Snapshot> snapshots() {
            return List.copyOf(events);
        }
    }
}
