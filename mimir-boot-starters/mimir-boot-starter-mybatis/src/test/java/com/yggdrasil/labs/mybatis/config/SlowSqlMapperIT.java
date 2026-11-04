package com.yggdrasil.labs.mybatis.config;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import javax.sql.DataSource;

import org.apache.ibatis.annotations.CacheNamespace;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.yggdrasil.labs.mybatis.log.SlowSqlInterceptor;
import com.yggdrasil.labs.test.util.LogTestUtils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class SlowSqlMapperIT {

    private static final String PARAMETER_SECRET = "parameter-secret-marker";
    private static final String STATEMENT_PREFIX = SlowSqlMapper.class.getName() + ".";
    private static final Set<String> PAYLOAD_FIELDS =
            Set.of(
                    "statementId",
                    "operation",
                    "elapsedMs",
                    "thresholdMs",
                    "timingScope",
                    "outcome",
                    "errorType",
                    "sql",
                    "sqlStatus");

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withUserConfiguration(MapperConfiguration.class)
                    .withPropertyValues("mimir.boot.mybatis.slow-sql.threshold-ms=1");

    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void captureSlowLogs() {
        logs = LogTestUtils.setupLogger("SQL.SLOW");
        ((Logger) LoggerFactory.getLogger("SQL.SLOW")).setLevel(Level.WARN);
    }

    @AfterEach
    void restoreLogger() {
        LogTestUtils.cleanupLogger("SQL.SLOW", logs);
    }

    @Test
    void automaticFactoryRegistersOnePluginAndRunsRealQueriesAndUpdates() {
        runner.run(
                context -> {
                    assertNull(context.getStartupFailure());
                    SqlSessionFactory factory = context.getBean(SqlSessionFactory.class);
                    SlowSqlInterceptor slow = context.getBean(SlowSqlInterceptor.class);
                    ProbeDataSource dataSource = context.getBean(ProbeDataSource.class);
                    prepareDatabase(dataSource);
                    installClock(slow);

                    assertEquals(1, context.getBeansOfType(SqlSessionFactory.class).size());
                    assertTrue(context.containsBean("sqlSessionFactory"));
                    assertPluginChain(factory, context.getBean(MybatisPlusInterceptor.class), slow);

                    SlowSqlMapper mapper = context.getBean(SlowSqlMapper.class);
                    assertEquals(PARAMETER_SECRET, mapper.readSecret(1L, PARAMETER_SECRET));
                    assertEquals(1, mapper.updateSecret(1L, "updated-secret-marker"));
                    assertEquals(
                            "updated-secret-marker",
                            jdbc(dataSource)
                                    .queryForObject(
                                            "SELECT private_column_marker FROM slow_private_account"
                                                    + " WHERE id=1",
                                            String.class));
                    assertEquals(2, dataSource.executedSql.size());
                    assertEquals(2, logs.list.size());

                    assertPayload(0, "readSecret", "QUERY", "SUCCESS");
                    assertPayload(1, "updateSecret", "UPDATE", "SUCCESS");
                    assertEquals(
                            "SELECT ? FROM ? WHERE ? = ? AND ? = ? AND ? = ?",
                            payload(0).getString("sql"));
                    assertEquals(
                            "UPDATE ? SET ? = ? WHERE ? = ? AND ? = ?",
                            payload(1).getString("sql"));
                });
    }

    @Test
    void customFactoryHasNoAutomaticSlowPluginAndKeepsPagination() {
        runner.withUserConfiguration(FactoryWithoutSlow.class)
                .run(
                        context -> {
                            assertNull(context.getStartupFailure());
                            ProbeDataSource dataSource = context.getBean(ProbeDataSource.class);
                            prepareDatabase(dataSource);
                            installClock(context.getBean(SlowSqlInterceptor.class));
                            SqlSessionFactory factory = context.getBean(SqlSessionFactory.class);
                            assertEquals(1, context.getBeansOfType(SqlSessionFactory.class).size());
                            assertFalse(context.containsBean("sqlSessionFactory"));
                            assertEquals(
                                    List.of(context.getBean(MybatisPlusInterceptor.class)),
                                    factory.getConfiguration().getInterceptors());

                            Page<SlowSqlRecord> page =
                                    context.getBean(SlowSqlMapper.class)
                                            .selectPage(new Page<>(1, 2), null);
                            assertEquals(3, page.getTotal());
                            assertEquals(2, page.getRecords().size());
                            assertEquals(1, dataSource.countExecutions());
                            assertEquals(2, dataSource.executedSql.size());
                            assertTrue(logs.list.isEmpty());
                        });
    }

    @Test
    void consumerMybatisPlusInterceptorKeepsItsOwnPaginationAndOrdering() {
        runner.withUserConfiguration(ConsumerPaginationConfiguration.class)
                .run(
                        context -> {
                            assertNull(context.getStartupFailure());
                            MybatisPlusInterceptor plugin =
                                    context.getBean(MybatisPlusInterceptor.class);
                            assertEquals(
                                    1, context.getBeansOfType(MybatisPlusInterceptor.class).size());
                            assertEquals(1, plugin.getInterceptors().size());
                            assertEquals(
                                    PaginationInnerInterceptor.class,
                                    plugin.getInterceptors().get(0).getClass());
                            assertTrue(
                                    context.getBean(SqlSessionFactory.class)
                                            .getConfiguration()
                                            .getInterceptors()
                                            .contains(plugin));
                        });
    }

    @Test
    void customFactoriesKeepPaginationWithAllBeanPluginsOrExplicitRegistration() {
        for (Class<?> configuration :
                List.of(FactoryWithAllBeanPlugins.class, FactoryWithExplicitPlugins.class)) {
            runner.withUserConfiguration(configuration)
                    .run(
                            context -> {
                                assertNull(context.getStartupFailure());
                                logs.list.clear();
                                ProbeDataSource dataSource = context.getBean(ProbeDataSource.class);
                                prepareDatabase(dataSource);
                                SlowSqlInterceptor slow = context.getBean(SlowSqlInterceptor.class);
                                installClock(slow);
                                SqlSessionFactory factory =
                                        context.getBean(SqlSessionFactory.class);
                                assertEquals(
                                        1, context.getBeansOfType(SqlSessionFactory.class).size());
                                assertFalse(context.containsBean("sqlSessionFactory"));
                                assertPluginChain(
                                        factory,
                                        context.getBean(MybatisPlusInterceptor.class),
                                        slow);

                                Page<SlowSqlRecord> page =
                                        context.getBean(SlowSqlMapper.class)
                                                .selectPage(new Page<>(1, 2), null);
                                assertEquals(3, page.getTotal());
                                assertEquals(2, page.getRecords().size());
                                assertEquals(1, dataSource.countExecutions());
                                assertEquals(2, dataSource.executedSql.size());
                                assertEquals(2, logs.list.size());
                                assertPayload(0, "selectList_mpCount", "QUERY", "SUCCESS");
                                assertPayload(1, "selectList", "QUERY", "SUCCESS");
                            });
        }
    }

    @Test
    void defaultPaginationUsesCurrentFactoryPluginsWhenFirstFactoryHasNoSlowPlugin() {
        runner.withUserConfiguration(FactoryWithoutSlow.class)
                .run(
                        first -> {
                            assertNull(first.getStartupFailure());
                            prepareDatabase(first.getBean(ProbeDataSource.class));
                            SqlSessionFactory firstFactory = first.getBean(SqlSessionFactory.class);
                            Page<SlowSqlRecord> firstPage =
                                    first.getBean(SlowSqlMapper.class)
                                            .selectPage(new Page<>(1, 2), null);
                            assertEquals(3, firstPage.getTotal());
                            runner.run(
                                    second -> {
                                        assertNull(second.getStartupFailure());
                                        ProbeDataSource secondDataSource =
                                                second.getBean(ProbeDataSource.class);
                                        createDatabase(secondDataSource);
                                        logs.list.clear();
                                        SlowSqlInterceptor slow =
                                                second.getBean(SlowSqlInterceptor.class);
                                        installClock(slow);
                                        SqlSessionFactory secondFactory =
                                                second.getBean(SqlSessionFactory.class);
                                        assertNotSame(firstFactory, secondFactory);
                                        assertNotSame(
                                                firstFactory.getConfiguration(),
                                                secondFactory.getConfiguration());
                                        assertPluginChain(
                                                secondFactory,
                                                second.getBean(MybatisPlusInterceptor.class),
                                                slow);

                                        Page<SlowSqlRecord> secondPage =
                                                second.getBean(SlowSqlMapper.class)
                                                        .selectPage(new Page<>(1, 2), null);
                                        assertEquals(3, secondPage.getTotal());
                                        assertEquals(2, secondPage.getRecords().size());
                                        assertEquals(1, secondDataSource.countExecutions());
                                        assertEquals(2, secondDataSource.executedSql.size());
                                        assertEquals(2, logs.list.size());
                                        assertPayload(0, "selectList_mpCount", "QUERY", "SUCCESS");
                                        assertPayload(1, "selectList", "QUERY", "SUCCESS");
                                    });
                        });
    }

    @Test
    void defaultPaginationDoesNotReuseSlowPluginWhenSecondContextDisablesIt() {
        runner.run(
                first -> {
                    assertNull(first.getStartupFailure());
                    prepareDatabase(first.getBean(ProbeDataSource.class));
                    installClock(first.getBean(SlowSqlInterceptor.class));
                    SqlSessionFactory firstFactory = first.getBean(SqlSessionFactory.class);
                    Page<SlowSqlRecord> firstPage =
                            first.getBean(SlowSqlMapper.class).selectPage(new Page<>(1, 2), null);
                    assertEquals(3, firstPage.getTotal());
                    assertEquals(2, logs.list.size());
                    runner.withPropertyValues("mimir.boot.mybatis.slow-sql.enabled=false")
                            .run(
                                    second -> {
                                        assertNull(second.getStartupFailure());
                                        ProbeDataSource dataSource =
                                                second.getBean(ProbeDataSource.class);
                                        createDatabase(dataSource);
                                        logs.list.clear();
                                        SqlSessionFactory secondFactory =
                                                second.getBean(SqlSessionFactory.class);
                                        assertTrue(
                                                second.getBeansOfType(SlowSqlInterceptor.class)
                                                        .isEmpty());
                                        assertFalse(
                                                second.getBean(SlowSqlProperties.class)
                                                        .isEnabled());
                                        assertEquals(
                                                List.of(
                                                        second.getBean(
                                                                MybatisPlusInterceptor.class)),
                                                secondFactory.getConfiguration().getInterceptors());
                                        assertNotSame(
                                                firstFactory.getConfiguration(),
                                                secondFactory.getConfiguration());

                                        Page<SlowSqlRecord> secondPage =
                                                second.getBean(SlowSqlMapper.class)
                                                        .selectPage(new Page<>(1, 2), null);
                                        assertEquals(3, secondPage.getTotal());
                                        assertEquals(2, secondPage.getRecords().size());
                                        assertEquals(1, dataSource.countExecutions());
                                        assertEquals(2, dataSource.executedSql.size());
                                        assertTrue(logs.list.isEmpty());
                                    });
                });
    }

    @Test
    void defaultPaginationUsesEachLiveFactorySlowThresholdInBothDirections() {
        for (long firstThreshold : List.of(1L, 3L)) {
            logs.list.clear();
            long secondThreshold = firstThreshold == 1 ? 3 : 1;
            runner.withPropertyValues("mimir.boot.mybatis.slow-sql.threshold-ms=" + firstThreshold)
                    .run(
                            first -> {
                                assertNull(first.getStartupFailure());
                                prepareDatabase(first.getBean(ProbeDataSource.class));
                                installClock(first.getBean(SlowSqlInterceptor.class));
                                assertEquals(
                                        3,
                                        first.getBean(SlowSqlMapper.class)
                                                .selectPage(new Page<>(1, 2), null)
                                                .getTotal());
                                assertEquals(firstThreshold == 1 ? 2 : 0, logs.list.size());

                                runner.withPropertyValues(
                                                "mimir.boot.mybatis.slow-sql.threshold-ms="
                                                        + secondThreshold)
                                        .run(
                                                second -> {
                                                    assertNull(second.getStartupFailure());
                                                    ProbeDataSource source =
                                                            second.getBean(ProbeDataSource.class);
                                                    prepareDatabase(source);
                                                    installClock(
                                                            second.getBean(
                                                                    SlowSqlInterceptor.class));
                                                    logs.list.clear();
                                                    Page<SlowSqlRecord> page =
                                                            second.getBean(SlowSqlMapper.class)
                                                                    .selectPage(
                                                                            new Page<>(1, 2), null);
                                                    assertEquals(3, page.getTotal());
                                                    assertEquals(2, page.getRecords().size());
                                                    assertEquals(1, source.countExecutions());
                                                    assertEquals(2, source.executedSql.size());
                                                    assertEquals(
                                                            secondThreshold == 1 ? 2 : 0,
                                                            logs.list.size());
                                                    if (secondThreshold == 1) {
                                                        assertPayload(
                                                                0,
                                                                "selectList_mpCount",
                                                                "QUERY",
                                                                "SUCCESS");
                                                        assertPayload(
                                                                1,
                                                                "selectList",
                                                                "QUERY",
                                                                "SUCCESS");
                                                    }
                                                });
                            });
        }
    }

    @Test
    void explicitCountIdUsesCurrentFactoryAndMissingIdFallsBackToAutomaticCount() {
        runner.withUserConfiguration(FactoryWithoutSlow.class)
                .run(
                        first -> {
                            prepareDatabase(first.getBean(ProbeDataSource.class));
                            Page<SlowSqlRecord> request = new Page<>(1, 2);
                            request.setCountId("countAll");
                            assertEquals(
                                    3,
                                    first.getBean(SlowSqlMapper.class)
                                            .selectPage(request, null)
                                            .getTotal());
                            runner.run(
                                    second -> {
                                        ProbeDataSource source =
                                                second.getBean(ProbeDataSource.class);
                                        prepareDatabase(source);
                                        installClock(second.getBean(SlowSqlInterceptor.class));
                                        SlowSqlMapper mapper = second.getBean(SlowSqlMapper.class);
                                        for (String countId :
                                                List.of(
                                                        "countAll",
                                                        STATEMENT_PREFIX + "countAll",
                                                        "missingCount")) {
                                            source.executedSql.clear();
                                            logs.list.clear();
                                            Page<SlowSqlRecord> page = new Page<>(1, 2);
                                            page.setCountId(countId);
                                            mapper.selectPage(page, null);
                                            assertEquals(3, page.getTotal());
                                            assertEquals(2, page.getRecords().size());
                                            assertEquals(1, source.countExecutions());
                                            assertEquals(2, logs.list.size());
                                            assertPayload(
                                                    0,
                                                    "missingCount".equals(countId)
                                                            ? "selectList_mpCount"
                                                            : "countAll",
                                                    "QUERY",
                                                    "SUCCESS");
                                            assertPayload(1, "selectList", "QUERY", "SUCCESS");
                                        }
                                    });
                        });
    }

    @Test
    void paginationTimesCountAndRecordsSeparatelyAndSearchCountFalseDoesNotRecount() {
        runner.run(
                context -> {
                    ProbeDataSource dataSource = context.getBean(ProbeDataSource.class);
                    prepareDatabase(dataSource);
                    installClock(context.getBean(SlowSqlInterceptor.class));
                    SlowSqlMapper mapper = context.getBean(SlowSqlMapper.class);

                    Page<SlowSqlRecord> automatic = mapper.selectPage(new Page<>(1, 2), null);
                    assertEquals(3, automatic.getTotal());
                    assertEquals(2, automatic.getRecords().size());
                    assertEquals(1, dataSource.countExecutions());
                    assertEquals(2, dataSource.executedSql.size());
                    assertEquals(2, logs.list.size());
                    assertPayload(0, "selectList_mpCount", "QUERY", "SUCCESS");
                    assertPayload(1, "selectList", "QUERY", "SUCCESS");

                    dataSource.executedSql.clear();
                    logs.list.clear();
                    long total = mapper.selectCount(null);
                    Page<SlowSqlRecord> counted = new Page<>(1, 2, false);
                    counted.setTotal(total);
                    mapper.selectPage(counted, null);
                    assertEquals(3, counted.getTotal());
                    assertEquals(2, counted.getRecords().size());
                    assertEquals(1, dataSource.countExecutions());
                    assertEquals(2, dataSource.executedSql.size());

                    dataSource.executedSql.clear();
                    logs.list.clear();
                    Page<SlowSqlRecord> firstTwo = mapper.selectPage(new Page<>(1, 2, false), null);
                    assertEquals(2, firstTwo.getRecords().size());
                    assertEquals(0, firstTwo.getTotal());
                    assertEquals(0, dataSource.countExecutions());
                    assertEquals(1, dataSource.executedSql.size());
                    assertEquals(1, logs.list.size());
                    assertPayload(0, "selectList", "QUERY", "SUCCESS");
                });
    }

    @Test
    void realMybatisWrappingPreservesTheOriginalSqlExceptionForEveryTimedOperation() {
        runner.run(
                context -> {
                    ProbeDataSource dataSource = context.getBean(ProbeDataSource.class);
                    prepareDatabase(dataSource);
                    installClock(context.getBean(SlowSqlInterceptor.class));
                    SqlSessionFactory factory = context.getBean(SqlSessionFactory.class);

                    for (String operation : List.of("QUERY", "UPDATE", "CURSOR_OPEN")) {
                        logs.list.clear();
                        dataSource.executedSql.clear();
                        SQLException original =
                                new SQLException("database-secret-message-marker", "42000", 987);
                        dataSource.nextExecutionFailure = original;

                        try (SqlSession session = factory.openSession()) {
                            SlowSqlMapper mapper = session.getMapper(SlowSqlMapper.class);
                            PersistenceException failure =
                                    assertThrows(
                                            PersistenceException.class,
                                            () -> {
                                                switch (operation) {
                                                    case "QUERY" ->
                                                            mapper.readSecret(1L, PARAMETER_SECRET);
                                                    case "UPDATE" ->
                                                            mapper.updateSecret(1L, "changed");
                                                    case "CURSOR_OPEN" -> mapper.openCursor();
                                                    default -> throw new AssertionError(operation);
                                                }
                                            });
                            assertSame(original, rootCause(failure));
                        }

                        assertEquals(1, dataSource.executedSql.size());
                        assertEquals(1, logs.list.size());
                        String method =
                                switch (operation) {
                                    case "QUERY" -> "readSecret";
                                    case "UPDATE" -> "updateSecret";
                                    default -> "openCursor";
                                };
                        assertPayload(0, method, operation, "ERROR");
                        assertEquals(
                                SQLException.class.getName(), payload(0).getString("errorType"));
                    }
                });
    }

    @Test
    void prepareFailureRemainsOutsideTheStatementCallTimingBoundary() {
        runner.run(
                context -> {
                    ProbeDataSource dataSource = context.getBean(ProbeDataSource.class);
                    prepareDatabase(dataSource);
                    AtomicLong clock = installClock(context.getBean(SlowSqlInterceptor.class));
                    SQLException original = new SQLException("prepare-secret-marker", "42000");
                    dataSource.nextPrepareFailure = original;

                    try (SqlSession session =
                            context.getBean(SqlSessionFactory.class).openSession()) {
                        PersistenceException failure =
                                assertThrows(
                                        PersistenceException.class,
                                        () ->
                                                session.getMapper(SlowSqlMapper.class)
                                                        .readSecret(1L, PARAMETER_SECRET));
                        assertSame(original, rootCause(failure));
                    }
                    assertTrue(dataSource.executedSql.isEmpty());
                    assertTrue(logs.list.isEmpty());
                    assertEquals(0, clock.get());
                });
    }

    @Test
    void firstAndSecondLevelCacheHitsDoNotReenterTheStatementPlugin() {
        runner.run(
                context -> {
                    ProbeDataSource dataSource = context.getBean(ProbeDataSource.class);
                    prepareDatabase(dataSource);
                    AtomicLong clock = installClock(context.getBean(SlowSqlInterceptor.class));
                    SqlSessionFactory factory = context.getBean(SqlSessionFactory.class);
                    factory.getConfiguration().addMapper(CachedSqlMapper.class);

                    try (SqlSession session = factory.openSession()) {
                        CachedSqlMapper mapper = session.getMapper(CachedSqlMapper.class);
                        assertEquals(PARAMETER_SECRET, mapper.readSecret(1L, PARAMETER_SECRET));
                        assertEquals(PARAMETER_SECRET, mapper.readSecret(1L, PARAMETER_SECRET));
                        session.commit();
                    }
                    try (SqlSession session = factory.openSession()) {
                        assertEquals(
                                PARAMETER_SECRET,
                                session.getMapper(CachedSqlMapper.class)
                                        .readSecret(1L, PARAMETER_SECRET));
                    }
                    assertEquals(1, dataSource.executedSql.size());
                    assertEquals(1, logs.list.size());
                    assertEquals(4_000_000L, clock.get());
                });
    }

    @Test
    void cursorIsTimedAtCreationAndNeverDuringTraversalOrClose() {
        runner.run(
                context -> {
                    ProbeDataSource dataSource = context.getBean(ProbeDataSource.class);
                    prepareDatabase(dataSource);
                    AtomicLong clock = installClock(context.getBean(SlowSqlInterceptor.class));

                    try (SqlSession session =
                                    context.getBean(SqlSessionFactory.class).openSession();
                            Cursor<SlowSqlRecord> cursor =
                                    session.getMapper(SlowSqlMapper.class).openCursor()) {
                        assertEquals(1, logs.list.size());
                        assertPayload(0, "openCursor", "CURSOR_OPEN", "SUCCESS");
                        assertEquals(4_000_000L, clock.get());
                        clock.addAndGet(10_000_000_000L);
                        List<Long> ids = new ArrayList<>();
                        cursor.forEach(record -> ids.add(record.id));
                        assertEquals(List.of(1L, 2L, 3L), ids);
                        assertEquals(10_004_000_000L, clock.get());
                    }
                    assertEquals(1, dataSource.executedSql.size());
                    assertEquals(1, logs.list.size());
                    assertEquals(10_004_000_000L, clock.get());
                });
    }

    @Test
    void batchEnqueueAndActualFlushDoNotPretendToBeTimedUpdateCalls() {
        runner.run(
                context -> {
                    ProbeDataSource dataSource = context.getBean(ProbeDataSource.class);
                    prepareDatabase(dataSource);
                    AtomicLong clock = installClock(context.getBean(SlowSqlInterceptor.class));

                    try (SqlSession session =
                            context.getBean(SqlSessionFactory.class)
                                    .openSession(ExecutorType.BATCH)) {
                        SlowSqlMapper mapper = session.getMapper(SlowSqlMapper.class);
                        mapper.updateSecret(1L, "batch-first");
                        mapper.updateSecret(2L, "batch-second");
                        assertEquals(2, dataSource.batchEnqueues);
                        assertTrue(dataSource.executedSql.isEmpty());
                        assertTrue(logs.list.isEmpty());
                        var batches = session.flushStatements();
                        assertEquals(1, batches.size());
                        assertEquals(2, batches.get(0).getUpdateCounts().length);
                        assertEquals(1, dataSource.batchFlushes);
                        session.commit();
                    }
                    assertEquals(
                            List.of("batch-first", "batch-second"),
                            jdbc(dataSource)
                                    .queryForList(
                                            "SELECT private_column_marker FROM slow_private_account"
                                                    + " WHERE id IN (1,2) ORDER BY id",
                                            String.class));
                    assertTrue(logs.list.isEmpty());
                    assertEquals(0, clock.get());
                });
    }

    @Test
    void afterSelectKeyProducesNestedEventsWhoseDurationsOverlap() {
        runner.run(
                context -> {
                    ProbeDataSource dataSource = context.getBean(ProbeDataSource.class);
                    prepareDatabase(dataSource);
                    installClock(context.getBean(SlowSqlInterceptor.class));
                    GeneratedRecord record = new GeneratedRecord();
                    record.id = 4L;
                    record.value = "generated-secret-marker";

                    assertEquals(
                            1, context.getBean(SlowSqlMapper.class).insertWithSelectKey(record));
                    assertEquals(4L, record.generatedId);
                    assertEquals(2, dataSource.executedSql.size());
                    assertEquals(2, logs.list.size());
                    assertPayload(0, "insertWithSelectKey!selectKey", "QUERY", "SUCCESS");
                    assertPayload(1, "insertWithSelectKey", "UPDATE", "SUCCESS");
                    assertEquals(2, payload(0).getBigDecimal("elapsedMs").intValueExact());
                    assertEquals(6, payload(1).getBigDecimal("elapsedMs").intValueExact());
                });
    }

    private JSONObject payload(int index) {
        return JSON.parseObject(logs.list.get(index).getFormattedMessage());
    }

    private void assertPayload(int index, String method, String operation, String outcome) {
        ILoggingEvent event = logs.list.get(index);
        JSONObject payload = payload(index);
        assertAll(
                () -> assertEquals(Level.WARN, event.getLevel()),
                () -> assertEquals("SQL.SLOW", event.getLoggerName()),
                () -> assertNull(event.getThrowableProxy()),
                () -> assertEquals(PAYLOAD_FIELDS, payload.keySet()),
                () -> assertEquals(STATEMENT_PREFIX + method, payload.getString("statementId")),
                () -> assertEquals(operation, payload.getString("operation")),
                () -> assertEquals(outcome, payload.getString("outcome")),
                () -> assertEquals("STATEMENT_CALL", payload.getString("timingScope")),
                () -> assertEquals(1, payload.getLongValue("thresholdMs")),
                () -> assertTrue(payload.getBigDecimal("elapsedMs").doubleValue() > 1),
                () -> assertEquals("SHAPE", payload.getString("sqlStatus")),
                () -> assertFalse(event.getFormattedMessage().contains("\n")),
                () -> assertFalse(event.getFormattedMessage().contains("\r")));
        for (String secret :
                List.of(
                        PARAMETER_SECRET,
                        "updated-secret-marker",
                        "literal-secret-marker",
                        "comment-secret-marker",
                        "database-secret-message-marker",
                        "slow_private_account",
                        "private_column_marker")) {
            assertFalse(event.getFormattedMessage().contains(secret), secret);
        }
        if ("SUCCESS".equals(outcome)) {
            assertNull(payload.get("errorType"));
        }
    }

    private static void assertPluginChain(
            SqlSessionFactory factory,
            MybatisPlusInterceptor mybatisPlus,
            SlowSqlInterceptor slow) {
        List<Interceptor> plugins = factory.getConfiguration().getInterceptors();
        assertEquals(2, plugins.size());
        assertEquals(1, plugins.stream().filter(SlowSqlInterceptor.class::isInstance).count());
        assertSame(
                slow,
                plugins.stream()
                        .filter(SlowSqlInterceptor.class::isInstance)
                        .findFirst()
                        .orElseThrow());
        assertTrue(plugins.contains(mybatisPlus));
        assertInstanceOf(PaginationInnerInterceptor.class, mybatisPlus.getInterceptors().get(0));
        assertInstanceOf(
                OptimisticLockerInnerInterceptor.class, mybatisPlus.getInterceptors().get(1));
    }

    private static AtomicLong installClock(SlowSqlInterceptor interceptor) {
        AtomicLong clock = new AtomicLong();
        LongSupplier supplier = () -> clock.getAndAdd(2_000_000L);
        // 保留自动配置创建的真实 Bean，仅替换时间边界，避免依赖 sleep 或机器负载。
        ReflectionTestUtils.setField(interceptor, "clock", supplier);
        return clock;
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static JdbcTemplate jdbc(ProbeDataSource dataSource) {
        return new JdbcTemplate(dataSource.getTargetDataSource());
    }

    private static void prepareDatabase(ProbeDataSource dataSource) {
        createDatabase(dataSource);
    }

    private static void createDatabase(ProbeDataSource dataSource) {
        JdbcTemplate jdbc = jdbc(dataSource);
        jdbc.execute(
                "CREATE TABLE slow_private_account (id BIGINT PRIMARY KEY, "
                        + "private_column_marker VARCHAR(100))");
        for (long id = 1; id <= 3; id++) {
            jdbc.update(
                    "INSERT INTO slow_private_account VALUES (?, ?)",
                    id,
                    id == 1 ? PARAMETER_SECRET : "row-" + id);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({
        MybatisPlusSlowSqlConfiguration.class,
        MybatisPlusAutoConfiguration.class,
        com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration.class
    })
    static class MapperConfiguration {
        @Bean(destroyMethod = "destroy")
        ProbeDataSource dataSource() {
            SingleConnectionDataSource target = new SingleConnectionDataSource();
            target.setDriverClassName("org.h2.Driver");
            target.setUrl("jdbc:h2:mem:slow_sql_" + UUID.randomUUID());
            target.setSuppressClose(true);
            return new ProbeDataSource(target);
        }

        @Bean
        MapperFactoryBean<SlowSqlMapper> slowSqlMapper(SqlSessionFactory factory) {
            MapperFactoryBean<SlowSqlMapper> mapper = new MapperFactoryBean<>(SlowSqlMapper.class);
            mapper.setSqlSessionFactory(factory);
            return mapper;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ConsumerPaginationConfiguration {
        @Bean
        MybatisPlusInterceptor consumerMybatisPlusInterceptor() {
            MybatisPlusInterceptor plugin = new MybatisPlusInterceptor();
            plugin.addInnerInterceptor(new PaginationInnerInterceptor());
            return plugin;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class FactoryWithoutSlow {
        @Bean
        SqlSessionFactory customSqlSessionFactory(
                DataSource dataSource, MybatisPlusInterceptor mybatisPlus) throws Exception {
            return factory(dataSource, mybatisPlus);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class FactoryWithAllBeanPlugins {
        @Bean
        SqlSessionFactory customSqlSessionFactory(DataSource dataSource, Interceptor[] plugins)
                throws Exception {
            return factory(dataSource, plugins);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class FactoryWithExplicitPlugins {
        @Bean
        SqlSessionFactory customSqlSessionFactory(
                DataSource dataSource, MybatisPlusInterceptor mybatisPlus, SlowSqlInterceptor slow)
                throws Exception {
            return factory(dataSource, mybatisPlus, slow);
        }
    }

    private static SqlSessionFactory factory(DataSource dataSource, Interceptor... plugins)
            throws Exception {
        MybatisSqlSessionFactoryBean bean = new MybatisSqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        bean.setPlugins(plugins);
        return bean.getObject();
    }

    public interface SlowSqlMapper extends BaseMapper<SlowSqlRecord> {
        @Select("SELECT COUNT(*) FROM slow_private_account")
        long countAll();

        @Select(
                "SELECT private_column_marker FROM slow_private_account "
                        + "WHERE id = #{id} AND private_column_marker = #{value} "
                        + "AND 'literal-secret-marker' = 'literal-secret-marker' "
                        + "/* comment-secret-marker */")
        String readSecret(@Param("id") long id, @Param("value") String value);

        @Update(
                "UPDATE slow_private_account SET private_column_marker = #{value} "
                        + "WHERE id = #{id} AND 271828 = 271828")
        int updateSecret(@Param("id") long id, @Param("value") String value);

        @Select("SELECT id, private_column_marker FROM slow_private_account ORDER BY id")
        Cursor<SlowSqlRecord> openCursor();

        @Insert("INSERT INTO slow_private_account VALUES (#{id}, #{value})")
        @SelectKey(
                statement = "SELECT MAX(id) FROM slow_private_account",
                keyProperty = "generatedId",
                before = false,
                resultType = Long.class)
        int insertWithSelectKey(GeneratedRecord record);
    }

    @CacheNamespace(readWrite = false)
    public interface CachedSqlMapper {
        @Select(
                "SELECT private_column_marker FROM slow_private_account "
                        + "WHERE id = #{id} AND private_column_marker = #{value}")
        String readSecret(@Param("id") long id, @Param("value") String value);
    }

    @TableName("slow_private_account")
    public static class SlowSqlRecord {
        @TableId(type = IdType.INPUT)
        private Long id;

        private String privateColumnMarker;
    }

    public static class GeneratedRecord {
        private long id;
        private long generatedId;
        private String value;
    }

    /** 仅在 JDBC 边界统计实际执行和注入异常，SQL、Mapper、插件链均使用真实实现。 */
    static class ProbeDataSource extends DelegatingDataSource {
        private final SingleConnectionDataSource target;
        private final List<String> executedSql = new ArrayList<>();
        private SQLException nextExecutionFailure;
        private SQLException nextPrepareFailure;
        private int batchEnqueues;
        private int batchFlushes;

        ProbeDataSource(SingleConnectionDataSource target) {
            super(target);
            this.target = target;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return wrapConnection(super.getConnection());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return wrapConnection(super.getConnection(username, password));
        }

        private Connection wrapConnection(Connection connection) {
            return (Connection)
                    Proxy.newProxyInstance(
                            Connection.class.getClassLoader(),
                            new Class<?>[] {Connection.class},
                            (proxy, method, args) -> {
                                if ("prepareStatement".equals(method.getName())) {
                                    SQLException failure = nextPrepareFailure;
                                    nextPrepareFailure = null;
                                    if (failure != null) {
                                        throw failure;
                                    }
                                    return wrapStatement(
                                            (PreparedStatement) invoke(connection, method, args),
                                            (String) args[0]);
                                }
                                return invoke(connection, method, args);
                            });
        }

        private PreparedStatement wrapStatement(PreparedStatement statement, String sql) {
            return (PreparedStatement)
                    Proxy.newProxyInstance(
                            PreparedStatement.class.getClassLoader(),
                            new Class<?>[] {PreparedStatement.class},
                            (proxy, method, args) -> {
                                String name = method.getName();
                                if ("addBatch".equals(name)) {
                                    batchEnqueues++;
                                }
                                if ("executeBatch".equals(name)) {
                                    batchFlushes++;
                                }
                                if (Set.of(
                                                "execute",
                                                "executeQuery",
                                                "executeUpdate",
                                                "executeBatch")
                                        .contains(name)) {
                                    executedSql.add(sql);
                                    SQLException failure = nextExecutionFailure;
                                    nextExecutionFailure = null;
                                    if (failure != null) {
                                        throw failure;
                                    }
                                }
                                return invoke(statement, method, args);
                            });
        }

        private long countExecutions() {
            return executedSql.stream()
                    .map(sql -> sql.toUpperCase(Locale.ROOT))
                    .filter(sql -> sql.matches("(?s)\\s*SELECT\\s+COUNT\\s*\\(.*"))
                    .count();
        }

        public void destroy() {
            target.destroy();
        }

        private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            }
        }
    }
}
