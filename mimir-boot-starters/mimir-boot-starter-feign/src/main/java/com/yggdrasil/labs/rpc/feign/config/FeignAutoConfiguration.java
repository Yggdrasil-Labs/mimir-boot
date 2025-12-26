package com.yggdrasil.labs.rpc.feign.config;

import com.yggdrasil.labs.rpc.core.hook.RpcHookChain;
import com.yggdrasil.labs.rpc.core.tracing.RpcTracerBridge;
import com.yggdrasil.labs.rpc.feign.client.RpcFeignClient;
import feign.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@AutoConfiguration
@EnableConfigurationProperties(FeignProperties.class)
@ConditionalOnProperty(prefix = "mimir.boot.feign", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FeignAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FeignAutoConfiguration.class);

    @Bean
    @Primary
    @ConditionalOnMissingBean
    public Client rpcFeignClient(
            ObjectProvider<Client> delegateProvider,
            RpcHookChain hookChain,
            RpcTracerBridge tracerBridge,
            FeignProperties properties) {
        Client delegate = delegateProvider.getIfAvailable(() -> new Client.Default(null, null));
        log.debug("Creating RpcFeignClient with enabled={}, contextPropagationEnabled={}, delegate={}",
                properties.isEnabled(),
                properties.isContextPropagationEnabled(),
                delegate != null ? delegate.getClass().getSimpleName() : "null");
        return new RpcFeignClient(delegate, hookChain, tracerBridge, properties);
    }
}

