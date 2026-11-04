package com.yggdrasil.labs.log.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * 服务器日志预设的文件滚动与异步队列参数
 *
 * <p>仅在选择 {@code classpath:com/yggdrasil/labs/log/logback/server.xml}
 * 时绑定与校验；容器预设和旧配置不读取。由启动早期处理器绑定，不注册为 Bean。
 *
 * @author Yggdrasil Labs
 * @since 2.4.0
 */
@ConfigurationProperties(prefix = "mimir.boot.log")
public class LogPresetProperties {

    static final String INTERNAL_PREFIX = "mimir.boot.log.internal.";

    private static final DataSize MIN_FILE_SIZE = DataSize.ofMegabytes(1);
    private static final DataSize MAX_FILE_SIZE = DataSize.ofGigabytes(1);
    private static final DataSize MAX_TOTAL_SIZE = DataSize.ofTerabytes(10);
    private static final int MAX_HISTORY_DAYS = 3650;
    private static final int MAX_QUEUE_SIZE = 1_048_576;
    private static final Duration MIN_FLUSH = Duration.ofMillis(1);
    private static final Duration MAX_FLUSH = Duration.ofSeconds(60);

    /** 文件滚动参数 */
    private final File file = new File();

    /** 异步队列参数 */
    private final Async async = new Async();

    public File getFile() {
        return file;
    }

    public Async getAsync() {
        return async;
    }

    /** 校验取值范围，失败时只报告属性名与合法范围，不回显值 */
    void validate() {
        requireRange(file.maxSize, MIN_FILE_SIZE, MAX_FILE_SIZE, "file.max-size", "1MB–1GB");
        requireRange(file.maxHistory, 1, MAX_HISTORY_DAYS, "file.max-history");
        requireRange(
                file.totalSizeCap,
                file.maxSize,
                MAX_TOTAL_SIZE,
                "file.total-size-cap",
                "max-size–10TB");
        requireRange(file.errorMaxHistory, 1, MAX_HISTORY_DAYS, "file.error-max-history");
        requireRange(
                file.errorTotalSizeCap,
                file.maxSize,
                MAX_TOTAL_SIZE,
                "file.error-total-size-cap",
                "max-size–10TB");
        requireRange(async.queueSize, 1, MAX_QUEUE_SIZE, "async.queue-size");
        if (async.maxFlushTime == null
                || async.maxFlushTime.compareTo(MIN_FLUSH) < 0
                || async.maxFlushTime.compareTo(MAX_FLUSH) > 0) {
            throw outOfRange("async.max-flush-time", "1ms–60s");
        }
    }

    /** 规范化为 XML springProperty 读取的内部键（字节数与毫秒数） */
    Map<String, Object> toInternalProperties() {
        Map<String, Object> resolved = new LinkedHashMap<>();
        resolved.put(
                INTERNAL_PREFIX + "file.max-size-bytes", String.valueOf(file.maxSize.toBytes()));
        resolved.put(INTERNAL_PREFIX + "file.max-history", String.valueOf(file.maxHistory));
        resolved.put(
                INTERNAL_PREFIX + "file.total-size-cap-bytes",
                String.valueOf(file.totalSizeCap.toBytes()));
        resolved.put(
                INTERNAL_PREFIX + "file.error-max-history", String.valueOf(file.errorMaxHistory));
        resolved.put(
                INTERNAL_PREFIX + "file.error-total-size-cap-bytes",
                String.valueOf(file.errorTotalSizeCap.toBytes()));
        resolved.put(INTERNAL_PREFIX + "async.queue-size", String.valueOf(async.queueSize));
        resolved.put(INTERNAL_PREFIX + "async.never-block", String.valueOf(async.neverBlock));
        resolved.put(
                INTERNAL_PREFIX + "async.max-flush-time-millis",
                String.valueOf(async.maxFlushTime.toMillis()));
        return resolved;
    }

    private static void requireRange(
            DataSize value, DataSize min, DataSize max, String name, String range) {
        if (value == null || value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw outOfRange(name, range);
        }
    }

    private static void requireRange(int value, int min, int max, String name) {
        if (value < min || value > max) {
            throw outOfRange(name, min + "–" + max);
        }
    }

    private static IllegalStateException outOfRange(String name, String range) {
        return new IllegalStateException("属性 mimir.boot.log." + name + " 超出合法范围 " + range);
    }

    /** 服务器文件滚动参数 */
    public static class File {

        /** 四类日志单文件上限，1MB–1GB */
        private DataSize maxSize = DataSize.ofMegabytes(100);

        /** info/access/sql 保留天数，1–3650 */
        private int maxHistory = 30;

        /** info/access/sql 各自总量上限，max-size–10TB */
        private DataSize totalSizeCap = DataSize.ofGigabytes(10);

        /** error 保留天数，1–3650 */
        private int errorMaxHistory = 60;

        /** error 总量上限，max-size–10TB */
        private DataSize errorTotalSizeCap = DataSize.ofGigabytes(5);

        public DataSize getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(DataSize maxSize) {
            this.maxSize = maxSize;
        }

        public int getMaxHistory() {
            return maxHistory;
        }

        public void setMaxHistory(int maxHistory) {
            this.maxHistory = maxHistory;
        }

        public DataSize getTotalSizeCap() {
            return totalSizeCap;
        }

        public void setTotalSizeCap(DataSize totalSizeCap) {
            this.totalSizeCap = totalSizeCap;
        }

        public int getErrorMaxHistory() {
            return errorMaxHistory;
        }

        public void setErrorMaxHistory(int errorMaxHistory) {
            this.errorMaxHistory = errorMaxHistory;
        }

        public DataSize getErrorTotalSizeCap() {
            return errorTotalSizeCap;
        }

        public void setErrorTotalSizeCap(DataSize errorTotalSizeCap) {
            this.errorTotalSizeCap = errorTotalSizeCap;
        }
    }

    /** 服务器异步队列参数 */
    public static class Async {

        /** 四个异步队列各自容量，1–1048576 */
        private int queueSize = 1024;

        /** 队列满时是否不阻塞；true 时包括 ERROR 在内的事件都可能丢弃 */
        private boolean neverBlock = false;

        /** 每个异步 appender 停止时的排空等待，1ms–60s */
        private Duration maxFlushTime = Duration.ofSeconds(1);

        public int getQueueSize() {
            return queueSize;
        }

        public void setQueueSize(int queueSize) {
            this.queueSize = queueSize;
        }

        public boolean isNeverBlock() {
            return neverBlock;
        }

        public void setNeverBlock(boolean neverBlock) {
            this.neverBlock = neverBlock;
        }

        public Duration getMaxFlushTime() {
            return maxFlushTime;
        }

        public void setMaxFlushTime(Duration maxFlushTime) {
            this.maxFlushTime = maxFlushTime;
        }
    }
}
