package com.yggdrasil.labs.rpc.core.config;

import com.yggdrasil.labs.rpc.core.hook.RpcHook;
import com.yggdrasil.labs.rpc.core.hook.RpcHookChain;
import com.yggdrasil.labs.rpc.core.support.RpcExecutionTemplate;
import com.yggdrasil.labs.rpc.core.tracing.NoopRpcTracerBridge;
import com.yggdrasil.labs.rpc.core.tracing.RpcTracerBridge;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(RpcCoreProperties.class)
@ConditionalOnProperty(prefix = "mimir.boot.rpc.core", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RpcCoreAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RpcCoreAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public RpcHookChain rpcHookChain(List<RpcHook> hooks) {
        log.debug("Creating RpcHookChain with {} hooks", hooks != null ? hooks.size() : 0);
        return new RpcHookChain(hooks);
    }

    @Bean
    @ConditionalOnMissingBean
    public RpcTracerBridge rpcTracerBridge() {
        log.debug("Creating NoopRpcTracerBridge as default RpcTracerBridge");
        return new NoopRpcTracerBridge();
    }

    @Bean
    @ConditionalOnMissingBean
    public RpcExecutionTemplate rpcExecutionTemplate(
            RpcHookChain hookChain, RpcTracerBridge tracerBridge, RpcCoreProperties properties) {
        log.debug("Creating RpcExecutionTemplate with contextPropagationEnabled={}",
                properties.isContextPropagationEnabled());
        return new RpcExecutionTemplate(hookChain, tracerBridge, properties.isContextPropagationEnabled());
    }
}

