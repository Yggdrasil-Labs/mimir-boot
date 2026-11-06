package com.yggdrasil.labs.rpc.feign.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.yggdrasil.labs.rpc.core.config.RpcCoreAutoConfiguration;
import com.yggdrasil.labs.rpc.core.hook.RpcHookChain;
import com.yggdrasil.labs.rpc.core.tracing.RpcTracerBridge;
import com.yggdrasil.labs.rpc.feign.client.RpcFeignCapability;

/** 将统一 RPC Hook 与上下文传播接入 Feign 客户端。 */
@AutoConfiguration(value = "mimirFeignAutoConfiguration", after = RpcCoreAutoConfiguration.class)
@EnableConfigurationProperties(FeignProperties.class)
@ConditionalOnProperty(
        prefix = "mimir.boot.feign",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class FeignAutoConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(FeignAutoConfiguration.class);

    /** 在 RPC 核心组件就绪后提供 Feign 客户端增强能力。 */
    @Bean
    @ConditionalOnProperty(
            prefix = "mimir.boot.rpc.core",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnBean({RpcHookChain.class, RpcTracerBridge.class})
    @ConditionalOnMissingBean(RpcFeignCapability.class)
    public RpcFeignCapability rpcFeignCapability(
            RpcHookChain hookChain, RpcTracerBridge tracerBridge, FeignProperties properties) {
        LOGGER.debug(
                "Creating RpcFeignCapability with enabled={}, contextPropagationEnabled={}",
                properties.isEnabled(),
                properties.isContextPropagationEnabled());
        return new RpcFeignCapability(hookChain, tracerBridge, properties);
    }
}
