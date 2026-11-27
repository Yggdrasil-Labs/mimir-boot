package com.yggdrasil.labs.log.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.logback.LogbackLoggingSystem;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.util.ContextInitializer;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;

/**
 * TD-049：旧 logback-spring.xml 按天滚动后的归档清理行为（design IC-7）
 *
 * <p>以受控时钟驱动真实的旧 XML appender。Logback 在每次 appender 启动后的首次清理只回溯 32 天，
 * 并按文件名日期判定周期，因此按月口径遗留的归档会同时出现"提前删除"和"长期残留"两个方向。
 */
class LegacyLogbackRollingTest {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    @TempDir Path logPath;

    private LoggerContext loggerContext;
    private LogbackLoggingSystem loggingSystem;

    @BeforeEach
    void setUp() {
        loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggingSystem = new LogbackLoggingSystem(getClass().getClassLoader());
        loggingSystem.cleanUp();
        MaskTestSupport.reset();
        System.setProperty("LOG_PATH", logPath.toString());
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles("test");
        environment
                .getPropertySources()
                .addLast(
                        new MapPropertySource(
                                "paths", Map.of("logging.file.path", logPath.toString())));
        loggingSystem.beforeInitialize();
        loggingSystem.initialize(
                new LoggingInitializationContext(environment),
                "classpath:logback-spring.xml",
                null);
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
    void info_archives_are_cleaned_by_file_name_date_after_upgrade() throws Exception {
        Rolling info = rolling("ASYNC_FILE_INFO", Level.INFO);
        Path tooOld = info.archive("info", LocalDate.of(2026, 9, 1));
        Path inWindow = info.archive("info", LocalDate.of(2026, 10, 1));
        Path retained = info.archive("info", LocalDate.of(2026, 10, 20));
        Path currentMonth = info.archive("info", LocalDate.of(2026, 11, 1));

        // 11-09 升级启动，11-10 首次滚动：首次清理覆盖文件名日期 09-09 至 10-10
        info.startAt(at(2026, 11, 9));
        info.rollAt(at(2026, 11, 10));

        assertThat(inWindow).as("文件名日期在回溯窗口内，即使内容较新也被删除").doesNotExist();
        assertThat(tooOld).as("早于保留天数 + 32 天，永不自动清理").exists();
        assertThat(retained).as("仍在 30 天保留期内").exists();
        assertThat(currentMonth).as("当月遗留归档首次清理不删除").exists();

        // 持续运行到 12-03：当月遗留归档按文件名日期满 30 天被删除
        info.rollAt(at(2026, 12, 3));

        assertThat(currentMonth).as("文件名日期 11-01 满保留期后删除").doesNotExist();
        assertThat(retained).as("文件名日期 10-20 满保留期后删除").doesNotExist();
        assertThat(tooOld).as("超出回溯窗口的遗留归档仍不清理").exists();
        info.stop();
    }

    @Test
    void error_archives_use_sixty_day_window_reaching_two_months_back() throws Exception {
        Rolling error = rolling("ASYNC_FILE_ERROR", Level.ERROR);
        Path tooOld = error.archive("error", LocalDate.of(2026, 8, 1));
        Path twoMonthsBack = error.archive("error", LocalDate.of(2026, 9, 1));
        Path retained = error.archive("error", LocalDate.of(2026, 9, 20));

        // 首次清理覆盖文件名日期 08-09 至 09-10
        error.startAt(at(2026, 11, 9));
        error.rollAt(at(2026, 11, 10));

        assertThat(twoMonthsBack).as("前两个月目录中的归档被首次清理删除").doesNotExist();
        assertThat(retained).as("仍在 60 天保留期内").exists();
        assertThat(tooOld).as("早于保留天数 + 32 天，永不自动清理").exists();
        error.stop();
    }

    @Test
    void only_restart_looks_back_again_while_continuous_run_does_not() throws Exception {
        Rolling info = rolling("ASYNC_FILE_INFO", Level.INFO);
        info.startAt(at(2026, 11, 9));
        info.rollAt(at(2026, 11, 10));
        // 首次清理已过，此后出现的过期归档只在周期新流逝时检查对应日期
        Path late = info.archive("info", LocalDate.of(2026, 10, 5));

        info.rollAt(at(2026, 11, 11));
        assertThat(late).as("持续运行时不回溯更早的周期").exists();

        info.stop();
        info.startAt(at(2026, 11, 11));
        info.rollAt(at(2026, 11, 12));
        assertThat(late).as("重启后首次清理重新回溯 32 天").doesNotExist();
        info.stop();
    }

    private Rolling rolling(String asyncName, Level level) {
        Logger root = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        AsyncAppender async = (AsyncAppender) root.getAppender(asyncName);
        Iterator<Appender<ILoggingEvent>> it = async.iteratorForAppenders();
        @SuppressWarnings("unchecked")
        RollingFileAppender<ILoggingEvent> appender =
                (RollingFileAppender<ILoggingEvent>) it.next();
        appender.stop();
        return new Rolling(appender, level, loggerContext.getLogger(getClass()));
    }

    private static long at(int year, int month, int day) {
        return LocalDateTime.of(year, month, day, 12, 0).atZone(ZONE).toInstant().toEpochMilli();
    }

    /** 直接驱动底层 RollingFileAppender，绕过异步队列以便同步控制滚动时机 */
    private static final class Rolling {

        private final RollingFileAppender<ILoggingEvent> appender;
        private final TimeBasedRollingPolicy<ILoggingEvent> policy;
        private final Level level;
        private final Logger logger;
        private final Path directory;

        @SuppressWarnings("unchecked")
        Rolling(RollingFileAppender<ILoggingEvent> appender, Level level, Logger logger) {
            this.appender = appender;
            this.policy = (TimeBasedRollingPolicy<ILoggingEvent>) appender.getRollingPolicy();
            this.level = level;
            this.logger = logger;
            this.directory = Path.of(appender.getFile()).getParent();
        }

        Path archive(String prefix, LocalDate date) throws IOException {
            Path file =
                    directory
                            .resolve(String.format("%tY-%<tm", date))
                            .resolve(String.format("%s-%s-0.log.gz", prefix, date));
            Files.createDirectories(file.getParent());
            Files.write(file, new byte[] {1});
            return file;
        }

        /** 模拟在指定时刻启动应用：活动文件修改时间决定启动时的当前周期 */
        void startAt(long time) throws IOException {
            Path active = Path.of(appender.getFile());
            Files.createDirectories(active.getParent());
            if (Files.notExists(active)) {
                Files.createFile(active);
            }
            Files.setLastModifiedTime(active, FileTime.fromMillis(time));
            policy.start();
            // start() 会重建触发器，必须在启动后设置模拟时间，避免首次写入使用真实日期。
            policy.getTimeBasedFileNamingAndTriggeringPolicy().setCurrentTime(time);
            appender.start();
            assertThat(policy.getTimeBasedFileNamingAndTriggeringPolicy().getCurrentTime())
                    .as("启动后的触发器必须保留模拟时间")
                    .isEqualTo(time);
            append();
        }

        /** 推进时钟并写入一条日志触发滚动，等待异步压缩与清理完成 */
        void rollAt(long time) throws Exception {
            policy.getTimeBasedFileNamingAndTriggeringPolicy().setCurrentTime(time);
            append();
            await("compressionFuture");
            await("cleanUpFuture");
        }

        void stop() {
            appender.stop();
        }

        private void append() {
            appender.doAppend(
                    new LoggingEvent(Logger.class.getName(), logger, level, "rolling", null, null));
        }

        private void await(String field) throws Exception {
            Future<?> future = (Future<?>) ReflectionTestUtils.getField(policy, field);
            if (future != null) {
                future.get(10, TimeUnit.SECONDS);
            }
        }
    }
}
