package com.yggdrasil.labs.rpc.dubbo.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import com.yggdrasil.labs.rpc.core.hook.RpcHookChain;
import com.yggdrasil.labs.rpc.core.tracing.RpcTracerBridge;
import com.yggdrasil.labs.rpc.dubbo.support.RpcDubboSupportHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DubboAutoConfigurationTest {

    private DubboAutoConfiguration configuration;
    private DubboProperties properties;
    private RpcHookChain hookChain;
    private RpcTracerBridge tracerBridge;

    @BeforeEach
    void setUp() {
        configuration = new DubboAutoConfiguration();
        properties = new DubboProperties();
        hookChain = mock(RpcHookChain.class);
        tracerBridge = mock(RpcTracerBridge.class);
    }

    @AfterEach
    void tearDown() {
        RpcDubboSupportHolder.set(null, null, null);
    }

    @Test
    void shouldCreateRpcDubboSupportHolder() {
        RpcDubboSupportHolder holder = configuration.rpcDubboSupportHolder(properties, hookChain, tracerBridge);

        assertNotNull(holder);
        assertSame(RpcDubboSupportHolder.getInstance(), holder);
        assertSame(hookChain, holder.getHookChain());
        assertSame(tracerBridge, holder.getTracerBridge());
        assertSame(properties, holder.getProperties());
    }

    @Test
    void shouldSetPropertiesCorrectly() {
        properties.setEnabled(false);
        properties.setContextPropagationEnabled(false);

        RpcDubboSupportHolder holder = configuration.rpcDubboSupportHolder(properties, hookChain, tracerBridge);

        assertNotNull(holder);
        assertFalse(holder.getProperties().isEnabled());
        assertFalse(holder.getProperties().isContextPropagationEnabled());
    }
}

