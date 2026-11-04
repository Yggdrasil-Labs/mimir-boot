package com.yggdrasil.labs.log.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 默认业务执行器 MDC 传播配置
 *
 * @author Yggdrasil Labs
 * @since 2.4.0
 */
@ConfigurationProperties(prefix = "mimir.boot.log.mdc")
public class MdcPropagationProperties {

    /** 是否为 Boot 默认平台执行器自动接入 MDC 传播；默认 false，完全沿用 Boot 默认执行器 */
    private boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
