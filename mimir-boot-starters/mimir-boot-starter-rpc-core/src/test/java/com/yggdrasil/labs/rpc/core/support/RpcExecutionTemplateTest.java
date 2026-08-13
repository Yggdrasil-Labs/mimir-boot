package com.yggdrasil.labs.rpc.core.support;

import com.yggdrasil.labs.rpc.core.context.RpcCallContext;
import com.yggdrasil.labs.rpc.core.context.RpcCallMetadata;
import com.yggdrasil.labs.rpc.core.context.RpcCallResult;
import com.yggdrasil.labs.rpc.core.hook.RpcHook;
import com.yggdrasil.labs.rpc.core.hook.RpcHookChain;
import com.yggdrasil.labs.rpc.core.tracing.RpcTracerBridge;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RpcExecutionTemplateTest {

    @BeforeAll
    static void enableDebugLogging() {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
    }

    @Test
    void shouldPropagateContextAndCallAfterOnSuccess() {
        RecordingHook hook = new RecordingHook();
        AtomicInteger tracerInvoked = new AtomicInteger();
        RpcTracerBridge tracer = new RpcTracerBridge() {
            @Override
            public Map<String, String> inject(RpcCallContext context) {
                tracerInvoked.incrementAndGet();
                return Map.of("trace-id", "tid-1");
            }

            @Override
            public void extract(RpcCallContext context, Map<String, String> carrier) {}
        };
        RpcExecutionTemplate template =
                new RpcExecutionTemplate(new RpcHookChain(List.of(hook)), tracer, true);
        RpcCallContext context = RpcCallContext.create(
                RpcCallMetadata.builder().service("svc").method("m1").build());

        String result = template.execute(context, () -> "ok");

        Assertions.assertEquals("ok", result);
        Assertions.assertEquals(List.of("before", "after", "cleanup"), hook.events);
        Assertions.assertEquals("tid-1", context.getAttachments().get("trace-id"));
        Assertions.assertEquals(1, tracerInvoked.get());
        Assertions.assertFalse(hook.onErrorCalled);
    }

    @Test
    void shouldExecuteWhenTracerReturnsNullHeaders() {
        RecordingHook hook = new RecordingHook();
        RpcTracerBridge tracer = new RpcTracerBridge() {
            @Override
            public Map<String, String> inject(RpcCallContext context) {
                return null;
            }

            @Override
            public void extract(RpcCallContext context, Map<String, String> carrier) {}
        };
        RpcExecutionTemplate template =
                new RpcExecutionTemplate(new RpcHookChain(List.of(hook)), tracer, true);
        RpcCallContext context = RpcCallContext.create(
                RpcCallMetadata.builder().service("svc").method("nullHeaders").build());

        String result = template.execute(context, () -> "ok");

        Assertions.assertEquals("ok", result);
        Assertions.assertTrue(context.getAttachments().isEmpty());
        Assertions.assertEquals(List.of("before", "after", "cleanup"), hook.events);
    }

    @Test
    void shouldCallOnErrorCleanupAndPropagateCheckedException() {
        RecordingHook hook = new RecordingHook();
        AtomicInteger tracerInvoked = new AtomicInteger();
        RpcTracerBridge tracer = new RpcTracerBridge() {
            @Override
            public Map<String, String> inject(RpcCallContext context) {
                tracerInvoked.incrementAndGet();
                return Map.of("ignored", "true");
            }

            @Override
            public void extract(RpcCallContext context, Map<String, String> carrier) {}
        };
        RpcExecutionTemplate template =
                new RpcExecutionTemplate(new RpcHookChain(List.of(hook)), tracer, false);
        RpcCallContext context = RpcCallContext.create(
                RpcCallMetadata.builder().service("svc").method("fail").build());

        Exception checked = new Exception("boom");
        Throwable thrown = Assertions.assertThrows(Throwable.class, () -> template.execute(context, asCallable(checked)));

        Assertions.assertSame(checked, thrown);
        Assertions.assertEquals(List.of("before", "onError", "cleanup"), hook.events);
        Assertions.assertEquals(0, tracerInvoked.get());
        Assertions.assertTrue(hook.onErrorCalled);
        Assertions.assertFalse(hook.afterCalled);
    }

    @Test
    void shouldPropagateSameCheckedBusinessExceptionWithSuppressedLifecycleFailures() {
        Exception primary = new Exception("business failure");
        RuntimeException onErrorFailure = new RuntimeException("onError failure");
        RuntimeException cleanupFailure = new RuntimeException("cleanup failure");
        RpcHook hook = new RpcHook() {
            @Override
            public void onError(RpcCallContext context, RpcCallResult result) {
                throw onErrorFailure;
            }

            @Override
            public void cleanup(RpcCallContext context) {
                throw cleanupFailure;
            }
        };
        RpcExecutionTemplate template = new RpcExecutionTemplate(
                new RpcHookChain(List.of(hook)), noOpTracer(), false);

        Throwable thrown = Assertions.assertThrows(
                Throwable.class, () -> template.execute(context("checked"), asCallable(primary)));

        Assertions.assertSame(primary, thrown);
        Assertions.assertArrayEquals(new Throwable[] {onErrorFailure, cleanupFailure}, thrown.getSuppressed());
    }

    @Test
    void shouldPropagateRuntimeExceptionWithoutWrapping() {
        RecordingHook hook = new RecordingHook();
        AtomicInteger tracerInvoked = new AtomicInteger();
        RpcTracerBridge tracer = new RpcTracerBridge() {
            @Override
            public Map<String, String> inject(RpcCallContext context) {
                tracerInvoked.incrementAndGet();
                return Map.of();
            }

            @Override
            public void extract(RpcCallContext context, Map<String, String> carrier) {}
        };
        RpcExecutionTemplate template =
                new RpcExecutionTemplate(new RpcHookChain(List.of(hook)), tracer, true);
        RpcCallContext context = RpcCallContext.create(
                RpcCallMetadata.builder().service("svc").method("runtime").build());

        RuntimeException runtime = new RuntimeException("boom");
        RuntimeException thrown = Assertions.assertThrows(
                RuntimeException.class, () -> template.execute(context, asCallable(runtime)));

        Assertions.assertSame(runtime, thrown);
        Assertions.assertTrue(hook.onErrorCalled);
        Assertions.assertFalse(hook.afterCalled);
        Assertions.assertEquals(List.of("before", "onError", "cleanup"), hook.events);
        Assertions.assertEquals(1, tracerInvoked.get());
    }

    @Test
    void shouldCompleteFailureLifecycleWhenBusinessThrowsError() {
        RecordingHook hook = new RecordingHook();
        RpcExecutionTemplate template = new RpcExecutionTemplate(
                new RpcHookChain(List.of(hook)), noOpTracer(), false);
        AssertionError primary = new AssertionError("fatal");

        AssertionError thrown = Assertions.assertThrows(
                AssertionError.class, () -> template.execute(context("error"), () -> {
                    throw primary;
                }));

        Assertions.assertSame(primary, thrown);
        Assertions.assertEquals(List.of("before", "onError", "cleanup"), hook.events);
    }

    @Test
    void shouldPreventBusinessCallAndCleanUpEnteredHooksWhenBeforeFails() {
        List<String> events = new ArrayList<>();
        AtomicInteger businessCalls = new AtomicInteger();
        RuntimeException beforeFailure = new RuntimeException("before failure");
        RpcHook first = hook("first", events, null, null, null);
        RpcHook second = hook("second", events, beforeFailure, null, null);
        RpcExecutionTemplate template = new RpcExecutionTemplate(
                new RpcHookChain(List.of(first, second)), noOpTracer(), true);

        RuntimeException thrown = Assertions.assertThrows(
                RuntimeException.class,
                () -> template.execute(context("beforeFailure"), () -> {
                    businessCalls.incrementAndGet();
                    return "unexpected";
                }));

        Assertions.assertSame(beforeFailure, thrown);
        Assertions.assertEquals(0, businessCalls.get());
        Assertions.assertEquals(
                List.of("first-before", "second-before", "first-onError", "second-onError", "second-cleanup", "first-cleanup"),
                events);
    }

    @Test
    void shouldCleanUpEnteredHooksInReverseOrderWhenTracerInjectionFails() {
        List<String> events = new ArrayList<>();
        AtomicInteger businessCalls = new AtomicInteger();
        RuntimeException primary = new RuntimeException("inject failure");
        RpcHook first = hook("first", events, null, null, null);
        RpcHook second = hook("second", events, null, null, null);
        RpcTracerBridge failingTracer = new RpcTracerBridge() {
            @Override
            public Map<String, String> inject(RpcCallContext context) {
                throw primary;
            }

            @Override
            public void extract(RpcCallContext context, Map<String, String> carrier) {
                // 该场景只验证调用侧注入失败，服务端提取路径不会执行。
            }
        };
        RpcExecutionTemplate template = new RpcExecutionTemplate(
                new RpcHookChain(List.of(first, second)), failingTracer, true);

        RuntimeException thrown = Assertions.assertThrows(
                RuntimeException.class,
                () -> template.execute(context("injectFailure"), () -> {
                    businessCalls.incrementAndGet();
                    return "unexpected";
                }));

        Assertions.assertSame(primary, thrown);
        Assertions.assertEquals(0, businessCalls.get());
        Assertions.assertEquals(
                List.of("first-before", "second-before", "first-onError", "second-onError", "second-cleanup", "first-cleanup"),
                events);
    }

    @Test
    void shouldPreserveBusinessOutcomeWhenPostHooksFail() {
        RuntimeException primary = new RuntimeException("business failure");
        RuntimeException onErrorFailure = new RuntimeException("onError failure");
        RuntimeException cleanupFailure = new RuntimeException("cleanup failure");
        RpcHook failing = hook("failing", new ArrayList<>(), null, null, onErrorFailure);
        RpcHook cleanupFailing = new RpcHook() {
            @Override
            public void cleanup(RpcCallContext context) {
                throw cleanupFailure;
            }
        };
        RpcExecutionTemplate template = new RpcExecutionTemplate(
                new RpcHookChain(List.of(failing, cleanupFailing)), noOpTracer(), false);

        RuntimeException thrown = Assertions.assertThrows(
                RuntimeException.class, () -> template.execute(context("failure"), asCallable(primary)));

        Assertions.assertSame(primary, thrown);
        Assertions.assertArrayEquals(new Throwable[] {onErrorFailure, cleanupFailure}, thrown.getSuppressed());
    }

    private static RpcCallContext context(String method) {
        return RpcCallContext.create(RpcCallMetadata.builder().service("svc").method(method).build());
    }

    private static RpcTracerBridge noOpTracer() {
        return new RpcTracerBridge() {
            @Override
            public Map<String, String> inject(RpcCallContext context) {
                return Map.of();
            }

            @Override
            public void extract(RpcCallContext context, Map<String, String> carrier) {
                // 此默认测试替身不维护服务端上下文，提取路径按设计无操作。
            }
        };
    }

    private static RpcHook hook(
            String name,
            List<String> events,
            RuntimeException beforeFailure,
            RuntimeException afterFailure,
            RuntimeException onErrorFailure) {
        return new RpcHook() {
            @Override
            public void before(RpcCallContext context) {
                events.add(name + "-before");
                if (beforeFailure != null) {
                    throw beforeFailure;
                }
            }

            @Override
            public void after(RpcCallContext context, RpcCallResult result) {
                if (afterFailure != null) {
                    throw afterFailure;
                }
            }

            @Override
            public void onError(RpcCallContext context, RpcCallResult result) {
                events.add(name + "-onError");
                if (onErrorFailure != null) {
                    throw onErrorFailure;
                }
            }

            @Override
            public void cleanup(RpcCallContext context) {
                events.add(name + "-cleanup");
            }
        };
    }

    private Callable<Void> asCallable(Exception toThrow) {
        return () -> {
            throw toThrow;
        };
    }

    private static class RecordingHook implements RpcHook {
        private final List<String> events = new ArrayList<>();
        private boolean onErrorCalled = false;
        private boolean afterCalled = false;

        @Override
        public void before(RpcCallContext context) {
            events.add("before");
        }

        @Override
        public void after(RpcCallContext context, RpcCallResult result) {
            afterCalled = true;
            events.add("after");
        }

        @Override
        public void onError(RpcCallContext context, RpcCallResult result) {
            onErrorCalled = true;
            events.add("onError");
        }

        @Override
        public void cleanup(RpcCallContext context) {
            events.add("cleanup");
        }
    }
}
