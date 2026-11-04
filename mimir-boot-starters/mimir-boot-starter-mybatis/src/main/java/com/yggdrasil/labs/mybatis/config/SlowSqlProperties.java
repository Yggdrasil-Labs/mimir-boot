package com.yggdrasil.labs.mybatis.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 慢 SQL 记录配置。 */
@ConfigurationProperties(prefix = "mimir.boot.mybatis.slow-sql")
public class SlowSqlProperties implements InitializingBean {
    private boolean enabled = true;
    private long thresholdMs = 1000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getThresholdMs() {
        return thresholdMs;
    }

    public void setThresholdMs(long thresholdMs) {
        this.thresholdMs = thresholdMs;
    }

    @Override
    public void afterPropertiesSet() {
        if (thresholdMs < 1 || thresholdMs > Long.MAX_VALUE / 1_000_000) {
            throw new IllegalArgumentException(
                    "mimir.boot.mybatis.slow-sql.threshold-ms 必须在 1 至 "
                            + (Long.MAX_VALUE / 1_000_000)
                            + " 毫秒之间");
        }
    }
}
