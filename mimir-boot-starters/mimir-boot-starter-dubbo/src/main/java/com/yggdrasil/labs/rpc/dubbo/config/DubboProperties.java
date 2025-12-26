package com.yggdrasil.labs.rpc.dubbo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mimir.boot.dubbo")
public class DubboProperties {

    /**
     * 是否启用 Dubbo 过滤与上下文处理，默认开启。
     */
    private boolean enabled = true;

    /**
     * 是否启用上下文传播（Trace/Span/Request-Id 等），默认开启。
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

