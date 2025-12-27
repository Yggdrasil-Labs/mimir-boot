package com.yggdrasil.labs.rpc.feign.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yggdrasil.labs.rpc.core.hook.RpcHookChain;
import com.yggdrasil.labs.rpc.core.tracing.RpcTracerBridge;
import com.yggdrasil.labs.rpc.feign.client.RpcFeignClient;
import feign.Client;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class FeignAutoConfigurationTest {

    private FeignAutoConfiguration configuration;
    private FeignProperties properties;
    private RpcHookChain hookChain;
    private RpcTracerBridge tracerBridge;
    @SuppressWarnings("unchecked")
    private ObjectProvider<Client> delegateProvider;

    @BeforeEach
    void setUp() {
        configuration = new FeignAutoConfiguration();
        properties = new FeignProperties();
        hookChain = mock(RpcHookChain.class);
        tracerBridge = mock(RpcTracerBridge.class);
        delegateProvider = mock(ObjectProvider.class);
    }

    @Test
    void shouldCreateRpcFeignClientWithDelegate() {
        Client delegate = mock(Client.class);
        when(delegateProvider.getIfAvailable()).thenReturn(delegate);

        Client client = configuration.rpcFeignClient(delegateProvider, hookChain, tracerBridge, properties);

        assertNotNull(client);
        assertSame(RpcFeignClient.class, client.getClass());
    }

    @Test
    void shouldCreateRpcFeignClientWithDefaultDelegate() {
        when(delegateProvider.getIfAvailable()).thenReturn(null);

        Client client = configuration.rpcFeignClient(delegateProvider, hookChain, tracerBridge, properties);

        assertNotNull(client);
        assertSame(RpcFeignClient.class, client.getClass());
    }

    @Test
    void shouldCreateRpcFeignClientWithProperties() {
        properties.setEnabled(false);
        properties.setContextPropagationEnabled(false);
        Client delegate = mock(Client.class);
        when(delegateProvider.getIfAvailable()).thenReturn(delegate);

        Client client = configuration.rpcFeignClient(delegateProvider, hookChain, tracerBridge, properties);

        assertNotNull(client);
        assertSame(RpcFeignClient.class, client.getClass());
    }
}

