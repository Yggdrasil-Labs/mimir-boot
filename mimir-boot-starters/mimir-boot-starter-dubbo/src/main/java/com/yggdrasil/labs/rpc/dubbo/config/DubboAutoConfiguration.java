package com.yggdrasil.labs.rpc.dubbo.config;

import com.yggdrasil.labs.rpc.core.config.RpcCoreAutoConfiguration;
import com.yggdrasil.labs.rpc.core.hook.RpcHookChain;
import com.yggdrasil.labs.rpc.core.tracing.RpcTracerBridge;
import com.yggdrasil.labs.rpc.dubbo.support.RpcDubboSupportHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = RpcCoreAutoConfiguration.class)
@EnableConfigurationProperties(DubboProperties.class)
@ConditionalOnProperty(prefix = "mimir.boot.dubbo", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DubboAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DubboAutoConfiguration.class);

    @Bean
    @ConditionalOnProperty(prefix = "mimir.boot.rpc.core", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnBean({RpcHookChain.class, RpcTracerBridge.class})
    public RpcDubboSupportHolder rpcDubboSupportHolder(
            DubboProperties properties, RpcHookChain hookChain, RpcTracerBridge tracerBridge) {
        log.debug("Creating RpcDubboSupportHolder with enabled={}, contextPropagationEnabled={}",
                properties.isEnabled(),
                properties.isContextPropagationEnabled());
        RpcDubboSupportHolder.set(hookChain, tracerBridge, properties);
        return RpcDubboSupportHolder.getInstance();
    }
}
