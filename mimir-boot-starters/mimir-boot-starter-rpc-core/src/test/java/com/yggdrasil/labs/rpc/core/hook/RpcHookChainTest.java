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
import java.util.concurrent.atomic.AtomicReference;
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
    void shouldProvideAsyncLifecycleWithoutAutoCloseableAndCleanupOnlyOnce() {
        AtomicInteger afterCalls = new AtomicInteger();
        AtomicInteger cleanupCalls = new AtomicInteger();
        RpcHook hook = new RpcHook() {
            @Override
            public void after(RpcCallContext context, RpcCallResult result) {
                afterCalls.incrementAndGet();
            }

            @Override
            public void cleanup(RpcCallContext context) {
                cleanupCalls.incrementAndGet();
            }
        };

        RpcAsyncHookInvocation invocation = new RpcHookChain(List.of(hook)).openAsync(context());

        Assertions.assertFalse(AutoCloseable.class.isAssignableFrom(invocation.getClass()));
        invocation.before();
        invocation.completeSuccess(RpcCallResult.success(Duration.ZERO));
        invocation.completeWithoutResult();

        Assertions.assertEquals(1, afterCalls.get());
        Assertions.assertEquals(1, cleanupCalls.get());
        Assertions.assertTrue(invocation.isClosed());
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

        CountDownLatch ready = new CountDownLatch(3);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            executor.submit(() -> awaitAndRun(ready, go, () -> first.completeSuccess(RpcCallResult.success(Duration.ZERO))));
            executor.submit(() -> awaitAndRun(ready, go, () -> first.completeFailure(
                    RpcCallResult.failure(Duration.ZERO, new RuntimeException("failure")), new RuntimeException("failure"))));
            executor.submit(() -> awaitAndRun(ready, go, first::close));
            Assertions.assertTrue(ready.await(5, TimeUnit.SECONDS));
            go.countDown();
            executor.shutdown();
            Assertions.assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        Assertions.assertEquals(1, cleanupCalls.get());
        Assertions.assertTrue(afterCalls.get() + errorCalls.get() <= 1);
        Assertions.assertTrue(first.isClosed());
        Assertions.assertFalse(second.isClosed());
    }

    @Test
    void shouldWaitForBeforeToFinishBeforeTerminalCleanup() throws Exception {
        List<String> events = java.util.Collections.synchronizedList(new ArrayList<>());
        CountDownLatch beforeStarted = new CountDownLatch(1);
        CountDownLatch releaseBefore = new CountDownLatch(1);
        CountDownLatch terminalStarted = new CountDownLatch(1);
        RpcHook hook = new RpcHook() {
            @Override
            public void before(RpcCallContext context) {
                events.add("before-started");
                beforeStarted.countDown();
                await(releaseBefore);
                events.add("before-finished");
            }

            @Override
            public void cleanup(RpcCallContext context) {
                events.add("cleanup");
            }
        };
        RpcHookInvocation invocation = new RpcHookChain(List.of(hook)).open(context());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var beforeFuture = executor.submit(invocation::before);
            Assertions.assertTrue(beforeStarted.await(5, TimeUnit.SECONDS));
            var terminalFuture = executor.submit(() -> {
                terminalStarted.countDown();
                invocation.completeSuccess(RpcCallResult.success(Duration.ZERO));
            });

            Assertions.assertTrue(terminalStarted.await(5, TimeUnit.SECONDS));
            Assertions.assertFalse(terminalFuture.isDone());

            releaseBefore.countDown();
            beforeFuture.get(5, TimeUnit.SECONDS);
            terminalFuture.get(5, TimeUnit.SECONDS);
        } finally {
            releaseBefore.countDown();
            executor.shutdownNow();
        }

        Assertions.assertEquals(List.of("before-started", "before-finished", "cleanup"), events);
        Assertions.assertTrue(invocation.isClosed());
    }

    @Test
    void shouldRejectTerminalReentryFromBeforePhase() {
        AtomicReference<RpcHookInvocation> invocationRef = new AtomicReference<>();
        AtomicInteger cleanupCalls = new AtomicInteger();
        RpcHook hook = new RpcHook() {
            @Override
            public void before(RpcCallContext context) {
                Assertions.assertThrows(
                        IllegalStateException.class,
                        () -> invocationRef.get().completeSuccess(RpcCallResult.success(Duration.ZERO)));
            }

            @Override
            public void cleanup(RpcCallContext context) {
                cleanupCalls.incrementAndGet();
            }
        };
        RpcHookInvocation invocation = new RpcHookChain(List.of(hook)).open(context());
        invocationRef.set(invocation);

        invocation.before();
        invocation.completeSuccess(RpcCallResult.success(Duration.ZERO));

        Assertions.assertEquals(1, cleanupCalls.get());
        Assertions.assertTrue(invocation.isClosed());
    }

    @Test
    void shouldKeepEnteredHooksSuppressedFailuresAndCleanupIsolatedAcrossThreads() throws Exception {
        List<String> cleanupEvents = java.util.Collections.synchronizedList(new ArrayList<>());
        RpcHook hook = new RpcHook() {
            @Override
            public void onError(RpcCallContext context, RpcCallResult result) {
                throw new IllegalStateException("onError-" + context.getMetadata().getMethod());
            }

            @Override
            public void cleanup(RpcCallContext context) {
                cleanupEvents.add(context.getMetadata().getMethod());
                throw new IllegalArgumentException("cleanup-" + context.getMetadata().getMethod());
            }
        };
        RpcHookChain chain = new RpcHookChain(List.of(hook));
        RpcHookInvocation first = chain.open(context("first"));
        RpcHookInvocation second = chain.open(context("second"));
        first.before();
        second.before();
        RuntimeException firstPrimary = new RuntimeException("first-primary");
        RuntimeException secondPrimary = new RuntimeException("second-primary");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            executor.submit(() -> awaitAndRun(ready, go, () -> first.completeFailure(
                    RpcCallResult.failure(Duration.ZERO, firstPrimary), firstPrimary)));
            executor.submit(() -> awaitAndRun(ready, go, () -> second.completeFailure(
                    RpcCallResult.failure(Duration.ZERO, secondPrimary), secondPrimary)));
            Assertions.assertTrue(ready.await(5, TimeUnit.SECONDS));
            go.countDown();
            executor.shutdown();
            Assertions.assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        assertSuppressedMessages(firstPrimary, "onError-first", "cleanup-first");
        assertSuppressedMessages(secondPrimary, "onError-second", "cleanup-second");
        Assertions.assertEquals(List.of("first", "second"), cleanupEvents.stream().sorted().toList());
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
        return context("m1");
    }

    private static RpcCallContext context(String method) {
        return RpcCallContext.create(RpcCallMetadata.builder().service("svc").method(method).build());
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

    private static void awaitAndRun(CountDownLatch ready, CountDownLatch go, Runnable action) {
        try {
            ready.countDown();
            go.await();
            action.run();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static void assertSuppressedMessages(Throwable throwable, String first, String second) {
        Throwable[] suppressed = throwable.getSuppressed();
        Assertions.assertEquals(2, suppressed.length);
        Assertions.assertEquals(first, suppressed[0].getMessage());
        Assertions.assertEquals(second, suppressed[1].getMessage());
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
