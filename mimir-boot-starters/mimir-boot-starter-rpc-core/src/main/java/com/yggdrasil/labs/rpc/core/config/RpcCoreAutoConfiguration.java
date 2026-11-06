package com.yggdrasil.labs.rpc.core.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.yggdrasil.labs.rpc.core.hook.RpcHook;
import com.yggdrasil.labs.rpc.core.hook.RpcHookChain;
import com.yggdrasil.labs.rpc.core.support.RpcExecutionTemplate;
import com.yggdrasil.labs.rpc.core.tracing.MdcRpcTracerBridge;
import com.yggdrasil.labs.rpc.core.tracing.RpcTracerBridge;

/** 装配 RPC Hook 链、默认追踪桥与调用执行模板。 */
@AutoConfiguration
@EnableConfigurationProperties(RpcCoreProperties.class)
@ConditionalOnProperty(
        prefix = "mimir.boot.rpc.core",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class RpcCoreAutoConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(RpcCoreAutoConfiguration.class);

    /** 使用容器中的 Hook 创建统一执行链。 */
    @Bean
    @ConditionalOnMissingBean
    public RpcHookChain rpcHookChain(List<RpcHook> hooks) {
        LOGGER.debug("Creating RpcHookChain with {} hooks", hooks != null ? hooks.size() : 0);
        return new RpcHookChain(hooks);
    }

    /** 在没有自定义追踪桥时提供 MDC 实现。 */
    @Bean
    @ConditionalOnMissingBean(RpcTracerBridge.class)
    public RpcTracerBridge rpcTracerBridge() {
        LOGGER.debug("Creating MdcRpcTracerBridge as default RpcTracerBridge");
        return new MdcRpcTracerBridge();
    }

    /** 将 Hook、追踪桥和传播开关绑定到调用模板。 */
    @Bean
    @ConditionalOnMissingBean
    public RpcExecutionTemplate rpcExecutionTemplate(
            RpcHookChain hookChain, RpcTracerBridge tracerBridge, RpcCoreProperties properties) {
        LOGGER.debug(
                "Creating RpcExecutionTemplate with contextPropagationEnabled={}",
                properties.isContextPropagationEnabled());
        return new RpcExecutionTemplate(
                hookChain, tracerBridge, properties.isContextPropagationEnabled());
    }
}
