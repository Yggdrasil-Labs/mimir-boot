package com.yggdrasil.labs.log.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 访问日志配置属性
 *
 * <p>用于配置访问日志功能</p>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "mimir.boot.log.access")
public class AccessLogProperties {

    /**
     * 是否启用访问日志功能
     * 默认：true
     */
    private boolean enabled = true;

    /**
     * 慢接口阈值（毫秒）
     * 超过此耗时的接口将被记录为 WARN 级别
     * 默认：1000ms（1秒）
     */
    private long slowThresholdMs = 1000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getSlowThresholdMs() {
        return slowThresholdMs;
    }

    public void setSlowThresholdMs(long slowThresholdMs) {
        this.slowThresholdMs = slowThresholdMs;
    }
}

