package com.yggdrasil.labs.rpc.core.hook;

import com.yggdrasil.labs.rpc.core.context.RpcCallContext;
import com.yggdrasil.labs.rpc.core.context.RpcCallMetadata;
import com.yggdrasil.labs.rpc.core.context.RpcCallResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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

    @Test
    void shouldCleanEnteredHooksInReverseOrderWhenBeforeFails() {
        List<String> events = new ArrayList<>();
        RuntimeException primary = new RuntimeException("before failure");
        RpcHook first = recordingHook("first", events, null, null);
        RpcHook second = recordingHook("second", events, primary, new IllegalStateException("cleanup failure"));
        RpcHookInvocation invocation = new RpcHookChain(List.of(first, second)).open(context());

        RuntimeException thrown = Assertions.assertThrows(RuntimeException.class, invocation::before);
        invocation.completeFailure(RpcCallResult.failure(Duration.ZERO, thrown), thrown);

        Assertions.assertSame(primary, thrown);
        Assertions.assertEquals(
                List.of("first-before", "second-before", "first-onError", "second-onError", "second-cleanup", "first-cleanup"),
                events);
        Assertions.assertEquals(1, thrown.getSuppressed().length);
        Assertions.assertEquals("cleanup failure", thrown.getSuppressed()[0].getMessage());
    }

    @Test
    void shouldKeepInvocationStateIsolatedAndAllowOnlyOneTerminalPath() throws Exception {
        AtomicInteger afterCalls = new AtomicInteger();
        AtomicInteger errorCalls = new AtomicInteger();
        AtomicInteger cleanupCalls = new AtomicInteger();
        RpcHook hook = new RpcHook() {
            @Override
            public void after(RpcCallContext context, RpcCallResult result) {
                afterCalls.incrementAndGet();
            }

            @Override
            public void onError(RpcCallContext context, RpcCallResult result) {
                errorCalls.incrementAndGet();
            }

            @Override
            public void cleanup(RpcCallContext context) {
                cleanupCalls.incrementAndGet();
            }
        };
        RpcHookChain chain = new RpcHookChain(List.of(hook));
        RpcHookInvocation first = chain.open(context());
        RpcHookInvocation second = chain.open(context());
        first.before();
        second.before();

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            executor.submit(() -> awaitAndRun(start, () -> first.completeSuccess(RpcCallResult.success(Duration.ZERO))));
            executor.submit(() -> awaitAndRun(start, () -> first.completeFailure(
                    RpcCallResult.failure(Duration.ZERO, new RuntimeException("failure")), new RuntimeException("failure"))));
            executor.submit(() -> awaitAndRun(start, first::close));
            start.countDown();
            executor.shutdown();
            Assertions.assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        second.completeSuccess(RpcCallResult.success(Duration.ZERO));

        Assertions.assertEquals(2, cleanupCalls.get());
        Assertions.assertEquals(2, afterCalls.get() + errorCalls.get());
        Assertions.assertTrue(first.isClosed());
        Assertions.assertTrue(second.isClosed());
    }

    @Test
    void shouldAttemptRemainingAfterHooksAndReverseCleanupWhenSuccessHooksFail() {
        List<String> events = new ArrayList<>();
        RpcHook first = successHook("first", events, new RuntimeException("after failure"));
        RpcHook second = successHook("second", events, null);
        RpcHookInvocation invocation = new RpcHookChain(List.of(first, second)).open(context());

        invocation.before();
        invocation.completeSuccess(RpcCallResult.success(Duration.ZERO));

        Assertions.assertEquals(
                List.of("first-before", "second-before", "first-after", "second-after", "second-cleanup", "first-cleanup"),
                events);
    }

    private static RpcCallContext context() {
        return RpcCallContext.create(RpcCallMetadata.builder().service("svc").method("m1").build());
    }

    private static RpcHook recordingHook(
            String name, List<String> events, RuntimeException beforeFailure, RuntimeException cleanupFailure) {
        return new RpcHook() {
            @Override
            public void before(RpcCallContext context) {
                events.add(name + "-before");
                if (beforeFailure != null) {
                    throw beforeFailure;
                }
            }

            @Override
            public void onError(RpcCallContext context, RpcCallResult result) {
                events.add(name + "-onError");
            }

            @Override
            public void cleanup(RpcCallContext context) {
                events.add(name + "-cleanup");
                if (cleanupFailure != null) {
                    throw cleanupFailure;
                }
            }
        };
    }

    private static RpcHook successHook(String name, List<String> events, RuntimeException afterFailure) {
        return new RpcHook() {
            @Override
            public void before(RpcCallContext context) {
                events.add(name + "-before");
            }

            @Override
            public void after(RpcCallContext context, RpcCallResult result) {
                events.add(name + "-after");
                if (afterFailure != null) {
                    throw afterFailure;
                }
            }

            @Override
            public void cleanup(RpcCallContext context) {
                events.add(name + "-cleanup");
            }
        };
    }

    private static void awaitAndRun(CountDownLatch start, Runnable action) {
        try {
            start.await();
            action.run();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
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
