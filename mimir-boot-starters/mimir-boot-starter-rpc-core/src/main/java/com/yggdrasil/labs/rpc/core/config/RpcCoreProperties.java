package com.yggdrasil.labs.rpc.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RPC Core 配置属性。
 *
 * <p>仅提供内核开关与上下文传递开关；具体治理/观测/安全能力由其他 Starter 插件提供。</p>
 */
@ConfigurationProperties(prefix = "mimir.boot.rpc.core")
public class RpcCoreProperties {

    /**
     * 是否启用 RPC Core，默认开启。
     */
    private boolean enabled = true;

    /**
     * 是否启用上下文传递（Trace/Span/Request-Id 等），默认开启。
     */
    private boolean contextPropagationEnabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isContextPropagationEnabled() {
        return contextPropagationEnabled;
    }

    public void setContextPropagationEnabled(boolean contextPropagationEnabled) {
        this.contextPropagationEnabled = contextPropagationEnabled;
    }
}

