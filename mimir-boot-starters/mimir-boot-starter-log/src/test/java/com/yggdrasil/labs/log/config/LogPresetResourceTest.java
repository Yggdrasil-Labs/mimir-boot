package com.yggdrasil.labs.log.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.logback.LogbackLoggingSystem;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.util.ContextInitializer;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import ch.qos.logback.core.util.FileSize;

/** 预设 XML：无内部源独立加载、springProperty 默认值与 IC-3 一致、实际 appender 参数及覆盖 */
class LogPresetResourceTest {

    private static final String BASE = "com/yggdrasil/labs/log/logback/";
    private static final Map<String, String> INTERNAL_DEFAULTS =
            Map.of(
                    "mimir.boot.log.internal.file.max-size-bytes", "104857600",
                    "mimir.boot.log.internal.file.max-history", "30",
                    "mimir.boot.log.internal.file.total-size-cap-bytes", "10737418240",
                    "mimir.boot.log.internal.file.error-max-history", "60",
                    "mimir.boot.log.internal.file.error-total-size-cap-bytes", "5368709120",
                    "mimir.boot.log.internal.async.queue-size", "1024",
                    "mimir.boot.log.internal.async.never-block", "false",
                    "mimir.boot.log.internal.async.max-flush-time-millis", "1000");

    @TempDir Path logPath;

    private LoggerContext loggerContext;
    private LogbackLoggingSystem loggingSystem;

    @BeforeEach
    void setUp() {
        loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggingSystem = new LogbackLoggingSystem(getClass().getClassLoader());
        loggingSystem.cleanUp();
        MaskTestSupport.reset();
    }

    @AfterEach
    void restore() throws Exception {
        loggingSystem.cleanUp();
        loggerContext.stop();
        loggerContext.reset();
        new ContextInitializer(loggerContext).autoConfig();
        System.clearProperty("LOG_PATH");
        MaskTestSupport.reset();
    }

    @Test
    void every_spring_property_declares_default_matching_ic3() throws Exception {
        Map<String, String> internal = new HashMap<>();
        Map<String, String> mask = new HashMap<>();
        for (String name : List.of("common.xml", "container.xml", "server.xml")) {
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(BASE + name)) {
                NodeList nodes =
                        DocumentBuilderFactory.newInstance()
                                .newDocumentBuilder()
                                .parse(input)
                                .getElementsByTagName("springProperty");
                for (int i = 0; i < nodes.getLength(); i++) {
                    Element element = (Element) nodes.item(i);
                    assertThat(element.hasAttribute("defaultValue"))
                            .as(name + " " + element.getAttribute("source"))
                            .isTrue();
                    String source = element.getAttribute("source");
                    if (source.startsWith("mimir.boot.log.internal.mask.")) {
                        mask.put(source, element.getAttribute("defaultValue"));
                        assertThat(element.getAttribute("scope")).isEqualTo("context");
                    } else if (source.startsWith("mimir.boot.log.internal.")) {
                        internal.put(source, element.getAttribute("defaultValue"));
                    } else {
                        assertThat(source).isEqualTo("spring.application.name");
                        assertThat(element.getAttribute("defaultValue")).isEqualTo("application");
                    }
                }
            }
        }
        assertThat(internal).isEqualTo(INTERNAL_DEFAULTS);
        assertThat(new LogPresetProperties().toInternalProperties()).isEqualTo(INTERNAL_DEFAULTS);
        // D-101：mask 内部键未写入时，启用列表取四组缺省，自定义与替换串为空
        assertThat(mask)
                .containsOnly(
                        Map.entry(
                                LogPresetSupport.INTERNAL_ENABLED_PATTERNS,
                                String.join(",", LogPresetSupport.PRESET_DEFAULT_PATTERNS)),
                        Map.entry(LogPresetSupport.INTERNAL_CUSTOM_PATTERNS, ""),
                        Map.entry(LogPresetSupport.INTERNAL_REPLACEMENT, ""));
    }

    @Test
    void server_loads_without_internal_source_using_defaults() {
        initialize("server.xml", environment(Map.of()));

        Logger root = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        assertThat(root.getLevel()).isEqualTo(Level.INFO);
        assertThat(appenderNames(root))
                .containsExactlyInAnyOrder("ASYNC_FILE_INFO", "ASYNC_FILE_ERROR");
        assertAsync(root, "ASYNC_FILE_INFO", 1024, false, 1000);
        RollingFileAppender<ILoggingEvent> info = file(root, "ASYNC_FILE_INFO");
        assertRolling(info, 104_857_600L, 30, 10_737_418_240L);
        assertRolling(file(root, "ASYNC_FILE_ERROR"), 104_857_600L, 60, 5_368_709_120L);
        assertThat(
                        ((AsyncAppender) root.getAppender("ASYNC_FILE_INFO"))
                                .getCopyOfAttachedFiltersList())
                .as("info.log 不设级别过滤")
                .isEmpty();
        assertThat(
                        ((AsyncAppender) root.getAppender("ASYNC_FILE_ERROR"))
                                .getCopyOfAttachedFiltersList())
                .as("ERROR 过滤在入队前")
                .hasSize(1);
        assertThat(file(root, "ASYNC_FILE_ERROR").getCopyOfAttachedFiltersList()).isEmpty();
        assertNoConsoleAppender();
        assertDedicatedLogger("access.log", Level.INFO, "ASYNC_FILE_ACCESS");
        assertDedicatedLogger("SQL.JSON", Level.INFO, "ASYNC_FILE_SQL");
        assertDedicatedLogger("SQL.SLOW", Level.WARN, "ASYNC_FILE_SQL");
    }

    @Test
    void server_uses_resolved_overrides() {
        StandardEnvironment environment =
                environment(
                        Map.of(
                                "logging.config", "classpath:" + BASE + "server.xml",
                                "mimir.boot.log.file.max-size", "50MB",
                                "mimir.boot.log.file.max-history", "7",
                                "mimir.boot.log.async.queue-size", "2048",
                                "mimir.boot.log.async.never-block", "true",
                                "mimir.boot.log.async.max-flush-time", "250ms"));
        new LogPresetEnvironmentPostProcessor(destination -> destination.get())
                .postProcessEnvironment(environment, new SpringApplication());

        initialize("server.xml", environment);

        Logger root = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        for (String name : List.of("ASYNC_FILE_INFO", "ASYNC_FILE_ERROR")) {
            assertAsync(root, name, 2048, true, 250);
        }
        assertRolling(file(root, "ASYNC_FILE_INFO"), 50L * 1024 * 1024, 7, 10_737_418_240L);
        assertRolling(file(root, "ASYNC_FILE_ERROR"), 50L * 1024 * 1024, 60, 5_368_709_120L);
        Logger access = loggerContext.getLogger("access.log");
        assertAsync(access, "ASYNC_FILE_ACCESS", 2048, true, 250);
        assertRolling(file(access, "ASYNC_FILE_ACCESS"), 50L * 1024 * 1024, 7, 10_737_418_240L);
    }

    @Test
    void container_has_console_only_and_creates_no_files() throws Exception {
        initialize("container.xml", environment(Map.of()));

        Logger root = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        assertThat(root.getLevel()).isEqualTo(Level.INFO);
        assertThat(appenderNames(root)).containsExactly("CONSOLE");
        assertThat(root.getAppender("CONSOLE")).isInstanceOf(ConsoleAppender.class);
        assertDedicatedLogger("access.log", Level.INFO, "CONSOLE");
        assertDedicatedLogger("SQL.JSON", Level.INFO, "CONSOLE");
        assertDedicatedLogger("SQL.SLOW", Level.WARN, "CONSOLE");
        for (Logger logger : loggerContext.getLoggerList()) {
            for (Iterator<Appender<ILoggingEvent>> it = logger.iteratorForAppenders();
                    it.hasNext(); ) {
                assertThat(it.next()).isNotInstanceOf(RollingFileAppender.class);
            }
        }
        root.error("container error");
        try (var files = Files.walk(logPath)) {
            assertThat(files.filter(Files::isRegularFile)).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"container.xml", "server.xml"})
    void prod_profile_applies_thirteen_warn_prefixes_without_spring_exceptions(String preset) {
        StandardEnvironment environment = environment(Map.of());
        environment.setActiveProfiles("prod");
        initialize(preset, environment);

        List<String> prefixes =
                List.of(
                        "org.springframework",
                        "org.apache",
                        "com.alibaba",
                        "com.baomidou",
                        "org.mybatis",
                        "com.zaxxer",
                        "com.mysql",
                        "org.postgresql",
                        "org.hibernate",
                        "io.netty",
                        "io.lettuce",
                        "redis.clients",
                        "org.reflections");
        for (String prefix : prefixes) {
            assertThat(loggerContext.getLogger(prefix).getLevel()).as(prefix).isEqualTo(Level.WARN);
        }
        assertThat(loggerContext.getLogger("org.springframework.context").getLevel()).isNull();
        assertThat(loggerContext.getLogger("io.lettuce.core").getLevel()).isNull();
        assertThat(loggerContext.getLogger("com.yggdrasil.labs").getLevel()).isNull();
    }

    @Test
    void default_profile_has_no_third_party_levels() {
        initialize("container.xml", environment(Map.of()));

        assertThat(loggerContext.getLogger("org.springframework").getLevel()).isNull();
    }

    private void initialize(String preset, StandardEnvironment environment) {
        // 未传 LogFile 时 Boot 不按 logging.file.path 设置 LOG_PATH，显式指向临时目录，避免在模块目录生成 logs/
        System.setProperty("LOG_PATH", logPath.toString());
        environment
                .getPropertySources()
                .addLast(
                        new MapPropertySource(
                                "paths", Map.of("logging.file.path", logPath.toString())));
        loggingSystem.beforeInitialize();
        loggingSystem.initialize(
                new LoggingInitializationContext(environment), "classpath:" + BASE + preset, null);
    }

    private static StandardEnvironment environment(Map<String, Object> properties) {
        StandardEnvironment environment = new StandardEnvironment();
        environment
                .getPropertySources()
                .addFirst(new MapPropertySource("test", new HashMap<>(properties)));
        return environment;
    }

    private void assertNoConsoleAppender() {
        for (Logger logger : loggerContext.getLoggerList()) {
            for (Iterator<Appender<ILoggingEvent>> it = logger.iteratorForAppenders();
                    it.hasNext(); ) {
                assertThat(it.next()).isNotInstanceOf(ConsoleAppender.class);
            }
        }
    }

    private void assertDedicatedLogger(String name, Level level, String appender) {
        Logger logger = loggerContext.getLogger(name);
        assertThat(logger.getLevel()).isEqualTo(level);
        assertThat(logger.isAdditive()).isFalse();
        assertThat(appenderNames(logger)).containsExactly(appender);
    }

    private static List<String> appenderNames(Logger logger) {
        List<String> names = new ArrayList<>();
        logger.iteratorForAppenders().forEachRemaining(a -> names.add(a.getName()));
        return names;
    }

    private static void assertAsync(
            Logger logger, String name, int queue, boolean neverBlock, int flush) {
        AsyncAppender async = (AsyncAppender) logger.getAppender(name);
        assertThat(async.getQueueSize()).isEqualTo(queue);
        assertThat(async.isNeverBlock()).isEqualTo(neverBlock);
        assertThat(async.getMaxFlushTime()).isEqualTo(flush);
        assertThat(async.getDiscardingThreshold()).isZero();
    }

    @SuppressWarnings("unchecked")
    private static RollingFileAppender<ILoggingEvent> file(Logger logger, String asyncName) {
        AsyncAppender async = (AsyncAppender) logger.getAppender(asyncName);
        Iterator<Appender<ILoggingEvent>> it = async.iteratorForAppenders();
        return (RollingFileAppender<ILoggingEvent>) it.next();
    }

    private static void assertRolling(
            RollingFileAppender<ILoggingEvent> appender, long maxSize, int history, long cap) {
        TimeBasedRollingPolicy<?> policy = (TimeBasedRollingPolicy<?>) appender.getRollingPolicy();
        assertThat(policy.getMaxHistory()).isEqualTo(history);
        // 归档目录的 %d 标记为 aux，按天滚动，max-history 以天计
        assertThat(
                        ((ch.qos.logback.core.rolling.helper.FileNamePattern)
                                        ReflectionTestUtils.getField(policy, "fileNamePattern"))
                                .getPrimaryDateTokenConverter()
                                .getDatePattern())
                .isEqualTo("yyyy-MM-dd");
        assertThat(((FileSize) ReflectionTestUtils.getField(policy, "totalSizeCap")).getSize())
                .isEqualTo(cap);
        assertThat(((FileSize) ReflectionTestUtils.getField(policy, "maxFileSize")).getSize())
                .isEqualTo(maxSize);
    }
}
