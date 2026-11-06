package com.yggdrasil.labs.rpc.dubbo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.yggdrasil.labs.rpc.core.config.RpcCoreAutoConfiguration;
import com.yggdrasil.labs.rpc.core.hook.RpcHookChain;
import com.yggdrasil.labs.rpc.core.tracing.RpcTracerBridge;
import com.yggdrasil.labs.rpc.dubbo.support.RpcDubboSupportHolder;

/** 为 Dubbo SPI 过滤器发布 Spring 管理的 RPC 依赖快照。 */
@AutoConfiguration(after = RpcCoreAutoConfiguration.class)
@EnableConfigurationProperties(DubboProperties.class)
@ConditionalOnProperty(
        prefix = "mimir.boot.dubbo",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class DubboAutoConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(DubboAutoConfiguration.class);

    /** 在 RPC 核心组件就绪后发布 Dubbo 过滤器使用的依赖。 */
    @Bean
    @ConditionalOnProperty(
            prefix = "mimir.boot.rpc.core",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnBean({RpcHookChain.class, RpcTracerBridge.class})
    public RpcDubboSupportHolder rpcDubboSupportHolder(
            DubboProperties properties, RpcHookChain hookChain, RpcTracerBridge tracerBridge) {
        LOGGER.debug(
                "Creating RpcDubboSupportHolder with enabled={}, contextPropagationEnabled={}",
                properties.isEnabled(),
                properties.isContextPropagationEnabled());
        RpcDubboSupportHolder.set(hookChain, tracerBridge, properties);
        return RpcDubboSupportHolder.getInstance();
    }
}
