package com.yggdrasil.labs.log.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

/** 预设输出路由：唯一事件 ID 按出口计数；队列满策略；标准输出故障不回退文件 */
@ExtendWith(OutputCaptureExtension.class)
class LogPresetOutputIntegrationTest {

    private static final String CONTAINER =
            "classpath:com/yggdrasil/labs/log/logback/container.xml";
    private static final String SERVER = "classpath:com/yggdrasil/labs/log/logback/server.xml";

    @TempDir Path logPath;
    private String id;

    @BeforeEach
    void setUp() {
        MaskTestSupport.reset();
        id = UUID.randomUUID().toString();
    }

    @AfterEach
    void tearDown() {
        MaskTestSupport.reset();
    }

    @Test
    void server_routes_each_event_to_expected_files_only(CapturedOutput output) throws IOException {
        try (ConfigurableApplicationContext ignoredContext =
                start(SERVER, "--spring.profiles.active=prod")) {
            emitAll();
        } finally {
            // 关闭 Spring 上下文不会排空 Logback 异步队列，统计出口前须等待日志停止。
            ((LoggerContext) LoggerFactory.getILoggerFactory()).stop();
        }
        Path dir = logPath.resolve("route-app");

        assertThat(count(dir.resolve("info.log"), "biz-info")).isEqualTo(1);
        assertThat(count(dir.resolve("info.log"), "biz-error")).isEqualTo(1);
        assertThat(count(dir.resolve("error.log"), "biz-error")).isEqualTo(1);
        assertThat(count(dir.resolve("error.log"), "biz-info")).isZero();
        assertThat(count(dir.resolve("access.log"), "access-info")).isEqualTo(1);
        assertThat(count(dir.resolve("access.log"), "access-error")).isEqualTo(1);
        assertThat(count(dir.resolve("error.log"), "access-error")).isZero();
        assertThat(count(dir.resolve("sql.log"), "sql-info")).isEqualTo(1);
        assertThat(count(dir.resolve("sql.log"), "sql-slow")).isEqualTo(1);
        for (String file : new String[] {"info.log", "error.log"}) {
            assertThat(count(dir.resolve(file), "sql-")).isZero();
            assertThat(count(dir.resolve(file), "access-")).isZero();
        }
        assertThat(output.getOut()).doesNotContain(id);
    }

    @Test
    void container_writes_each_event_once_to_stdout_and_creates_no_files(CapturedOutput output)
            throws IOException {
        try (ConfigurableApplicationContext ignoredContext =
                start(CONTAINER, "--spring.profiles.active=prod")) {
            emitAll();
        }
        for (String event :
                new String[] {"biz-info", "biz-error", "access-info", "sql-info", "sql-slow"}) {
            assertThat(output.getOut().lines().filter(l -> l.contains(event + " " + id)).count())
                    .as(event)
                    .isEqualTo(1);
        }
        try (Stream<Path> files = Files.walk(logPath)) {
            assertThat(files.filter(Files::isRegularFile)).isEmpty();
        }
    }

    @Test
    void prod_third_party_warn_can_be_overridden_by_logging_level(CapturedOutput output) {
        try (ConfigurableApplicationContext ignoredContext =
                start(CONTAINER, "--spring.profiles.active=prod")) {
            LoggerFactory.getLogger("org.springframework.context.Probe")
                    .info("spring-default {}", id);
        }
        try (ConfigurableApplicationContext ignoredContext =
                start(
                        CONTAINER,
                        "--spring.profiles.active=prod",
                        "--logging.level.org.springframework=INFO")) {
            LoggerFactory.getLogger("org.springframework.context.Probe")
                    .info("spring-override {}", id);
        }
        assertThat(output.getOut())
                .doesNotContain("spring-default " + id)
                .contains("spring-override " + id);
    }

    @Test
    void full_queue_blocks_by_default_and_drops_when_never_block() throws Exception {
        assertThat(producerBlocks(false)).as("默认等待队列空间").isTrue();
        assertThat(producerBlocks(true)).as("never-block=true 不阻塞").isFalse();
    }

    @Test
    void stdout_failure_does_not_fall_back_to_files() throws Exception {
        PrintStream original = System.out;
        PrintStream failing =
                new PrintStream(
                        new OutputStream() {
                            @Override
                            public void write(int b) throws IOException {
                                throw new IOException("stdout broken");
                            }
                        },
                        true,
                        StandardCharsets.UTF_8);
        try (ConfigurableApplicationContext ignoredContext = start(CONTAINER)) {
            System.setOut(failing);
            LoggerFactory.getLogger("demo.route").error("stdout-failure {}", id);
            System.setOut(original);
            assertThat(failing.checkError()).as("测试侧确认底层写入失败").isTrue();
        } finally {
            System.setOut(original);
            failing.close();
        }
        try (Stream<Path> files = Files.walk(logPath)) {
            assertThat(files.filter(Files::isRegularFile)).as("不创建备用文件").isEmpty();
        }
    }

    private boolean producerBlocks(boolean neverBlock) throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(1);
        try (ConfigurableApplicationContext ignoredContext =
                start(
                        SERVER,
                        "--mimir.boot.log.async.queue-size=1",
                        "--mimir.boot.log.async.never-block=" + neverBlock)) {
            ch.qos.logback.classic.Logger root =
                    (ch.qos.logback.classic.Logger)
                            LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
            AsyncAppender async = (AsyncAppender) root.getAppender("ASYNC_FILE_INFO");
            async.detachAndStopAllAppenders();
            // AsyncAppender 只允许挂一个下游 appender，替换为可暂停的下游前重置计数
            org.springframework.test.util.ReflectionTestUtils.setField(async, "appenderCount", 0);
            AppenderBase<ILoggingEvent> blocking =
                    new AppenderBase<>() {
                        @Override
                        protected void append(ILoggingEvent event) {
                            entered.countDown();
                            try {
                                release.await(5, TimeUnit.SECONDS);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    };
            blocking.setContext(async.getContext());
            blocking.start();
            async.addAppender(blocking);
            Logger logger = LoggerFactory.getLogger("demo.queue");
            CompletableFuture<Void> producer =
                    CompletableFuture.runAsync(
                            () -> {
                                for (int i = 0; i < 4; i++) {
                                    logger.info("queue {} {}", id, i);
                                }
                            });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            boolean blocked;
            try {
                producer.get(500, TimeUnit.MILLISECONDS);
                blocked = false;
            } catch (java.util.concurrent.TimeoutException expected) {
                blocked = true;
            }
            release.countDown();
            producer.get(5, TimeUnit.SECONDS);
            return blocked;
        } finally {
            release.countDown();
        }
    }

    private void emitAll() {
        Logger business = LoggerFactory.getLogger("demo.route");
        business.info("biz-info {}", id);
        business.error("biz-error {}", id);
        LoggerFactory.getLogger("access.log").info("access-info {}", id);
        LoggerFactory.getLogger("access.log").error("access-error {}", id);
        LoggerFactory.getLogger("SQL.JSON").info("sql-info {}", id);
        LoggerFactory.getLogger("SQL.SLOW").warn("sql-slow {}", id);
    }

    private long count(Path file, String event) throws IOException {
        if (!Files.exists(file)) {
            return 0;
        }
        try (Stream<String> lines = Files.lines(file)) {
            return lines.filter(l -> l.contains(event) && l.contains(id)).count();
        }
    }

    private ConfigurableApplicationContext start(String config, String... extra) {
        SpringApplication application = new SpringApplication(App.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("spring.application.name", "route-app");
        application.setDefaultProperties(defaults);
        String[] args = new String[extra.length + 2];
        args[0] = "--logging.config=" + config;
        args[1] = "--logging.file.path=" + logPath;
        System.arraycopy(extra, 0, args, 2, extra.length);
        return application.run(args);
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(LogMaskAutoConfiguration.class)
    static class App {}
}
