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
    void shouldCallOnErrorCleanupAndWrapCheckedException() {
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
        RuntimeException thrown = Assertions.assertThrows(
                RuntimeException.class, () -> template.execute(context, asCallable(checked)));

        Assertions.assertSame(checked, thrown.getCause());
        Assertions.assertEquals(List.of("before", "onError", "cleanup"), hook.events);
        Assertions.assertEquals(0, tracerInvoked.get());
        Assertions.assertTrue(hook.onErrorCalled);
        Assertions.assertFalse(hook.afterCalled);
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
