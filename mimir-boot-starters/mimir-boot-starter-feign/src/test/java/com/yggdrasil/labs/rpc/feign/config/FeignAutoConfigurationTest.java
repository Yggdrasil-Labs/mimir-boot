package com.yggdrasil.labs.rpc.feign.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import com.yggdrasil.labs.rpc.core.hook.RpcHookChain;
import com.yggdrasil.labs.rpc.core.tracing.RpcTracerBridge;
import com.yggdrasil.labs.rpc.feign.client.RpcFeignCapability;
import com.yggdrasil.labs.rpc.feign.client.RpcFeignClient;
import feign.Client;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FeignAutoConfigurationTest {

    private FeignAutoConfiguration configuration;
    private FeignProperties properties;
    private RpcHookChain hookChain;
    private RpcTracerBridge tracerBridge;

    @BeforeEach
    void setUp() {
        configuration = new FeignAutoConfiguration();
        properties = new FeignProperties();
        hookChain = mock(RpcHookChain.class);
        tracerBridge = mock(RpcTracerBridge.class);
    }

    @Test
    void shouldCreateRpcFeignCapability() {
        RpcFeignCapability capability = configuration.rpcFeignCapability(hookChain, tracerBridge, properties);

        assertNotNull(capability);
    }

    @Test
    void shouldWrapDelegateClient() {
        Client delegate = mock(Client.class);

        Client client = configuration.rpcFeignCapability(hookChain, tracerBridge, properties).enrich(delegate);

        assertNotNull(client);
        assertSame(RpcFeignClient.class, client.getClass());
    }

    @Test
    void shouldReuseExistingRpcFeignClient() {
        properties.setEnabled(false);
        properties.setContextPropagationEnabled(false);
        Client delegate = mock(Client.class);
        RpcFeignClient existing = new RpcFeignClient(delegate, hookChain, tracerBridge, properties);

        Client client = configuration.rpcFeignCapability(hookChain, tracerBridge, properties).enrich(existing);

        assertSame(existing, client);
    }
}
