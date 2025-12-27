package com.yggdrasil.labs.rpc.core.hook;

import com.yggdrasil.labs.rpc.core.context.RpcCallContext;
import com.yggdrasil.labs.rpc.core.context.RpcCallMetadata;
import com.yggdrasil.labs.rpc.core.context.RpcCallResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RpcHookChainTest {

    @BeforeAll
    static void enableDebugLogging() {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
    }

    @Test
    void shouldInvokeHooksInOrder() {
        List<String> events = new ArrayList<>();
        OrderedRecordingHook first = new OrderedRecordingHook("first", 1, events);
        OrderedRecordingHook second = new OrderedRecordingHook("second", 2, events);

        RpcHookChain chain = new RpcHookChain(List.of(second, first)); // intentionally shuffled
        RpcCallContext context = RpcCallContext.create(
                RpcCallMetadata.builder().service("svc").method("m1").build());
        RpcCallResult result = RpcCallResult.success(Duration.ofMillis(5));

        chain.before(context);
        chain.after(context, result);
        chain.onError(context, RpcCallResult.failure(Duration.ofMillis(5), new RuntimeException("boom")));
        chain.cleanup(context);

        Assertions.assertEquals(
                List.of("first-before", "second-before", "first-after", "second-after", "first-onError", "second-onError", "first-cleanup", "second-cleanup"),
                events);
        Assertions.assertTrue(first.invokedBefore(second));
        Assertions.assertTrue(second.invokedAfter(first));
    }

    @Test
    void shouldHandleEmptyHooksGracefully() {
        RpcHookChain chain = new RpcHookChain(List.of());
        RpcCallContext context = RpcCallContext.create(
                RpcCallMetadata.builder().service("svc").method("m1").build());
        RpcCallResult success = RpcCallResult.success(Duration.ZERO);
        RpcCallResult failure = RpcCallResult.failure(Duration.ZERO, new RuntimeException("err"));

        Assertions.assertDoesNotThrow(() -> {
            chain.before(context);
            chain.after(context, success);
            chain.onError(context, failure);
            chain.cleanup(context);
        });
    }

    private static class OrderedRecordingHook implements RpcHook {
        private final String name;
        private final int orderValue;
        private final StringBuilder order = new StringBuilder();
        private final List<String> events;

        OrderedRecordingHook(String name, int orderValue) {
            this(name, orderValue, new ArrayList<>());
        }

        OrderedRecordingHook(String name, int orderValue, List<String> events) {
            this.name = name;
            this.orderValue = orderValue;
            this.events = events;
        }

        @Override
        public void before(RpcCallContext context) {
            order.append("before");
            events.add(name + "-before");
        }

        @Override
        public void after(RpcCallContext context, RpcCallResult result) {
            order.append("after");
            events.add(name + "-after");
        }

        @Override
        public void onError(RpcCallContext context, RpcCallResult result) {
            order.append("onError");
            events.add(name + "-onError");
        }

        @Override
        public void cleanup(RpcCallContext context) {
            order.append("cleanup");
            events.add(name + "-cleanup");
        }

        boolean invokedBefore(OrderedRecordingHook other) {
            return this.orderValue < other.orderValue;
        }

        boolean invokedAfter(OrderedRecordingHook other) {
            return this.orderValue > other.orderValue;
        }

        @Override
        public int getOrder() {
            return orderValue;
        }
    }
}

