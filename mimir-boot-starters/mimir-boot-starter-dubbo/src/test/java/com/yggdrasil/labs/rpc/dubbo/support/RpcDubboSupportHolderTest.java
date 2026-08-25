package com.yggdrasil.labs.rpc.dubbo.support;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

import com.yggdrasil.labs.rpc.core.hook.RpcHookChain;
import com.yggdrasil.labs.rpc.core.tracing.RpcTracerBridge;
import com.yggdrasil.labs.rpc.dubbo.config.DubboProperties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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

    @Test
    void shouldPublishAllDependenciesAsOneSnapshot() {
        RpcDubboSupportHolder.set(hookChain, tracerBridge, properties);

        RpcDubboSupportHolder.Snapshot snapshot = RpcDubboSupportHolder.current();

        assertSame(hookChain, snapshot.hookChain());
        assertSame(tracerBridge, snapshot.tracerBridge());
        assertSame(properties, snapshot.properties());
    }

    @Test
    void shouldNeverExposeMixedGenerationDuringConcurrentPublication() throws Exception {
        RpcHookChain firstHookChain = mock(RpcHookChain.class);
        RpcTracerBridge firstTracerBridge = mock(RpcTracerBridge.class);
        DubboProperties firstProperties = new DubboProperties();
        RpcHookChain secondHookChain = mock(RpcHookChain.class);
        RpcTracerBridge secondTracerBridge = mock(RpcTracerBridge.class);
        DubboProperties secondProperties = new DubboProperties();
        AtomicBoolean mixedGeneration = new AtomicBoolean();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        RpcDubboSupportHolder.set(firstHookChain, firstTracerBridge, firstProperties);
        try {
            var writer = executor.submit(() -> {
                await(start);
                for (int index = 0; index < 10_000; index++) {
                    RpcDubboSupportHolder.set(firstHookChain, firstTracerBridge, firstProperties);
                    RpcDubboSupportHolder.set(secondHookChain, secondTracerBridge, secondProperties);
                }
            });
            var reader = executor.submit(() -> {
                await(start);
                for (int index = 0; index < 10_000; index++) {
                    RpcDubboSupportHolder.Snapshot snapshot = RpcDubboSupportHolder.current();
                    boolean firstGeneration = snapshot.hookChain() == firstHookChain
                            && snapshot.tracerBridge() == firstTracerBridge
                            && snapshot.properties() == firstProperties;
                    boolean secondGeneration = snapshot.hookChain() == secondHookChain
                            && snapshot.tracerBridge() == secondTracerBridge
                            && snapshot.properties() == secondProperties;
                    if (!firstGeneration && !secondGeneration) {
                        mixedGeneration.set(true);
                        return;
                    }
                }
            });
            start.countDown();
            writer.get(5, TimeUnit.SECONDS);
            reader.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertFalse(mixedGeneration.get());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }
}
