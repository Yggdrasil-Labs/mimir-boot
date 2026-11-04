package com.yggdrasil.labs.mybatis.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.yggdrasil.labs.mybatis.log.SlowSqlInterceptor;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/** 验证 JSON SQL 与 slow SQL 的日志开关及自动配置可独立组合。 */
class SlowSqlJsonCombinationIT {

    private static final String SQL_SECRET = "sql-secret-marker";
    private static final String PARAM_SECRET = "parameter-secret-marker";

    private ListAppender<ILoggingEvent> jsonLogs;
    private ListAppender<ILoggingEvent> slowLogs;

    @BeforeEach
    void captureLogs() {
        jsonLogs = attach("SQL.JSON", Level.INFO);
        slowLogs = attach("SQL.SLOW", Level.WARN);
    }

    @AfterEach
    void detachLogs() {
        detach("SQL.JSON", jsonLogs);
        detach("SQL.SLOW", slowLogs);
    }

    @Test
    void jsonAndSlowSqlFlagsOperateIndependentlyAcrossFourCombinations() {
        for (boolean jsonEnabled : new boolean[] {false, true}) {
            for (boolean slowEnabled : new boolean[] {false, true}) {
                jsonLogs.list.clear();
                slowLogs.list.clear();
                runner(jsonEnabled, slowEnabled)
                        .run(
                                context -> {
                                    assertNull(context.getStartupFailure());
                                    SqlSessionFactory factory =
                                            context.getBean(SqlSessionFactory.class);
                                    if (slowEnabled) {
                                        installClock(context.getBean(SlowSqlInterceptor.class));
                                    }
                                    createSchema(context.getBean(SingleConnectionDataSource.class));

                                    assertEquals(
                                            PARAM_SECRET,
                                            context.getBean(SecretMapper.class)
                                                    .readSecret(PARAM_SECRET));

                                    assertEquals(jsonEnabled ? 1 : 0, jsonLogs.list.size());
                                    assertEquals(slowEnabled ? 1 : 0, slowLogs.list.size());
                                    assertEquals(
                                            1 + (slowEnabled ? 1 : 0),
                                            factory.getConfiguration().getInterceptors().size());

                                    if (jsonEnabled) {
                                        ILoggingEvent event = jsonLogs.list.get(0);
                                        assertEquals(Level.INFO, event.getLevel());
                                        JSONObject payload =
                                                JSON.parseObject(event.getFormattedMessage());
                                        assertTrue(payload.getString("sql").contains("SELECT"));
                                        assertTrue(payload.getString("sql").contains("WHERE"));
                                        assertTrue(payload.containsKey("params"));
                                        assertTrue(payload.toJSONString().contains(PARAM_SECRET));
                                    }
                                    if (slowEnabled) {
                                        ILoggingEvent event = slowLogs.list.get(0);
                                        assertEquals(Level.WARN, event.getLevel());
                                        assertFalse(
                                                event.getFormattedMessage().contains(SQL_SECRET));
                                        assertFalse(
                                                event.getFormattedMessage().contains(PARAM_SECRET));
                                    }
                                });
            }
        }
    }

    @Test
    void jsonDefaultProfilesAndExplicitOverridesAreReflectedByContainerBeans() {
        for (String profile : new String[] {"dev", "local", "development", "test", "prod"}) {
            boolean expectedDefault = !"prod".equals(profile);
            assertJsonBean(profile, null, expectedDefault);
            assertJsonBean(profile, true, true);
            assertJsonBean(profile, false, false);
        }
    }

    private static ApplicationContextRunner runner(boolean jsonEnabled, boolean slowEnabled) {
        return new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(
                                MybatisPlusLoggingConfiguration.class,
                                MybatisPlusSlowSqlConfiguration.class,
                                com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration
                                        .class))
                .withUserConfiguration(MapperConfiguration.class)
                .withPropertyValues(
                        "mimir.boot.mybatis.enable-json-sql-log=" + jsonEnabled,
                        "mimir.boot.mybatis.slow-sql.enabled=" + slowEnabled,
                        "mimir.boot.mybatis.slow-sql.threshold-ms=1");
    }

    private static void assertJsonBean(String profile, Boolean explicit, boolean expected) {
        ApplicationContextRunner runner =
                new ApplicationContextRunner()
                        .withConfiguration(
                                AutoConfigurations.of(MybatisPlusLoggingConfiguration.class))
                        .withPropertyValues(
                                explicit == null
                                        ? new String[0]
                                        : new String[] {
                                            "mimir.boot.mybatis.enable-json-sql-log=" + explicit
                                        })
                        .withInitializer(
                                context -> context.getEnvironment().setActiveProfiles(profile));
        runner.run(
                context -> {
                    assertTrue(context.getStartupFailure() == null);
                    var interceptors = context.getBeansOfType(InnerInterceptor.class);
                    if (Boolean.FALSE.equals(explicit)) {
                        assertTrue(interceptors.isEmpty());
                        return;
                    }

                    assertEquals(1, interceptors.size());
                    InnerInterceptor interceptor = interceptors.values().iterator().next();
                    if (expected) {
                        assertEquals(
                                "JsonSqlLogInnerInterceptor",
                                interceptor.getClass().getSimpleName());
                    } else {
                        assertEquals(
                                "NoOpInnerInterceptor", interceptor.getClass().getSimpleName());
                    }
                });
    }

    private static void createSchema(SingleConnectionDataSource dataSource) {
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE secret_row (secret_value VARCHAR(100))");
            statement.execute("INSERT INTO secret_row VALUES ('" + PARAM_SECRET + "')");
        } catch (Exception exception) {
            throw new AssertionError("H2 测试表初始化失败", exception);
        }
    }

    private static void installClock(SlowSqlInterceptor interceptor) {
        AtomicLong ticks = new AtomicLong();
        ReflectionTestUtils.setField(
                interceptor, "clock", (LongSupplier) () -> ticks.getAndAdd(2_000_000));
    }

    private static ListAppender<ILoggingEvent> attach(String name, Level level) {
        Logger logger = (Logger) LoggerFactory.getLogger(name);
        logger.setLevel(level);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static void detach(String name, ListAppender<ILoggingEvent> appender) {
        if (appender != null) {
            ((Logger) LoggerFactory.getLogger(name)).detachAppender(appender);
            appender.stop();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class MapperConfiguration {
        @Bean
        MybatisPlusInterceptor mybatisPlusInterceptor(
                MybatisProperties properties, ListableBeanFactory beanFactory) {
            return new MybatisPlusAutoConfiguration()
                    .mybatisPlusInterceptor(properties, beanFactory);
        }

        @Bean
        SingleConnectionDataSource dataSource() {
            SingleConnectionDataSource dataSource = new SingleConnectionDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:json_slow_" + UUID.randomUUID());
            dataSource.setSuppressClose(true);
            return dataSource;
        }

        @Bean
        MapperFactoryBean<SecretMapper> secretMapper(SqlSessionFactory factory) {
            MapperFactoryBean<SecretMapper> mapper = new MapperFactoryBean<>(SecretMapper.class);
            mapper.setSqlSessionFactory(factory);
            return mapper;
        }
    }

    public interface SecretMapper {
        @Select(
                "SELECT secret_value FROM secret_row WHERE secret_value = #{value} AND '"
                        + SQL_SECRET
                        + "' <> ''")
        String readSecret(@Param("value") String value);
    }
}
