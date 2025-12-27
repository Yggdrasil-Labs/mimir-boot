package com.yggdrasil.labs.rpc.dubbo.support;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import com.yggdrasil.labs.rpc.core.hook.RpcHookChain;
import com.yggdrasil.labs.rpc.core.tracing.RpcTracerBridge;
import com.yggdrasil.labs.rpc.dubbo.config.DubboProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RpcDubboSupportHolderTest {

    private RpcHookChain hookChain;
    private RpcTracerBridge tracerBridge;
    private DubboProperties properties;

    @BeforeEach
    void setUp() {
        hookChain = mock(RpcHookChain.class);
        tracerBridge = mock(RpcTracerBridge.class);
        properties = new DubboProperties();
    }

    @AfterEach
    void tearDown() {
        RpcDubboSupportHolder.set(null, null, null);
    }

    @Test
    void shouldGetSameInstance() {
        RpcDubboSupportHolder instance1 = RpcDubboSupportHolder.getInstance();
        RpcDubboSupportHolder instance2 = RpcDubboSupportHolder.getInstance();
        assertSame(instance1, instance2);
    }

    @Test
    void shouldSetAndGetValues() {
        RpcDubboSupportHolder.set(hookChain, tracerBridge, properties);
        RpcDubboSupportHolder holder = RpcDubboSupportHolder.getInstance();

        assertSame(hookChain, holder.getHookChain());
        assertSame(tracerBridge, holder.getTracerBridge());
        assertSame(properties, holder.getProperties());
    }

    @Test
    void shouldHandleNullValues() {
        RpcDubboSupportHolder.set(null, null, null);
        RpcDubboSupportHolder holder = RpcDubboSupportHolder.getInstance();

        assertNull(holder.getHookChain());
        assertNull(holder.getTracerBridge());
        assertNull(holder.getProperties());
    }

    @Test
    void shouldUpdateValues() {
        RpcDubboSupportHolder.set(hookChain, tracerBridge, properties);
        RpcDubboSupportHolder holder1 = RpcDubboSupportHolder.getInstance();
        assertNotNull(holder1.getHookChain());

        RpcHookChain newHookChain = mock(RpcHookChain.class);
        RpcDubboSupportHolder.set(newHookChain, tracerBridge, properties);
        RpcDubboSupportHolder holder2 = RpcDubboSupportHolder.getInstance();
        assertSame(newHookChain, holder2.getHookChain());
    }
}

