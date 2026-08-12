package com.yggdrasil.labs.rpc.dubbo.filter;

import com.yggdrasil.labs.rpc.core.context.RpcCallContext;
import com.yggdrasil.labs.rpc.core.context.RpcCallResult;
import com.yggdrasil.labs.rpc.core.hook.RpcHook;
import com.yggdrasil.labs.rpc.core.hook.RpcHookChain;
import com.yggdrasil.labs.rpc.core.tracing.RpcTracerBridge;
import com.yggdrasil.labs.rpc.core.tracing.RpcTraceScope;
import com.yggdrasil.labs.rpc.dubbo.config.DubboProperties;
import com.yggdrasil.labs.rpc.dubbo.support.RpcDubboSupportHolder;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.AsyncRpcResult;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcInvocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

class RpcDubboFilterTest {

    private final RpcHook hook = mock(RpcHook.class);
    private final RpcHookChain hookChain = new RpcHookChain(List.of(hook));
    private final RpcTracerBridge tracerBridge = mock(RpcTracerBridge.class);
    private final DubboProperties properties = new DubboProperties();
    private final RpcDubboFilter filter = new RpcDubboFilter();

    @BeforeEach
    void setUp() {
        properties.setEnabled(true);
        properties.setContextPropagationEnabled(true);
        RpcDubboSupportHolder.set(hookChain, tracerBridge, properties);
    }

    @Test
    void shouldInvokeHooksAndTracer() {
        Invocation invocation = mock(Invocation.class);
        Invoker<?> invoker = mockInvoker();
        Result result = mock(Result.class);
        when(invoker.invoke(invocation)).thenReturn(result);
        when(tracerBridge.inject(any())).thenReturn(Map.of("x-trace-id", "t1"));
        when(invocation.getMethodName()).thenReturn("m1");
        when(invocation.getObjectAttachments()).thenReturn(Map.of());

        Result actual = filter.invoke(invoker, invocation);

        assertSame(result, actual);
        verify(hook).before(any());
        verify(hook).after(any(), any(RpcCallResult.class));
        verify(hook).cleanup(any());
        verify(tracerBridge).inject(any());
        verify(invocation).setAttachment("x-trace-id", "t1");
    }

    @Test
    void shouldExtractProviderContextBeforeHooksWithoutInjectingAttachments() {
        Invocation invocation = mock(Invocation.class);
        Invoker<?> invoker = mockInvoker(CommonConstants.PROVIDER_SIDE);
        Result result = mock(Result.class);
        Map<String, Object> attachments = Map.of("x-trace-id", "upstream-trace");
        when(invoker.invoke(invocation)).thenReturn(result);
        when(invocation.getMethodName()).thenReturn("m1");
        when(invocation.getObjectAttachments()).thenReturn(attachments);

        Result actual = filter.invoke(invoker, invocation);

        assertSame(result, actual);
        ArgumentCaptor<RpcCallContext> contextCaptor = ArgumentCaptor.forClass(RpcCallContext.class);
        InOrder inOrder = inOrder(tracerBridge, hook, invoker);
        inOrder.verify(tracerBridge).extractScope(contextCaptor.capture(), eq(Map.of("x-trace-id", "upstream-trace")));
        inOrder.verify(hook).before(contextCaptor.getValue());
        inOrder.verify(invoker).invoke(invocation);
        verify(tracerBridge, never()).inject(any());
        verify(invocation, never()).setAttachment(anyString(), anyString());
    }

    @Test
    void shouldCloseProviderTraceScopeBeforeReturningToCaller() {
        Invocation invocation = mock(Invocation.class);
        Invoker<?> invoker = mockInvoker(CommonConstants.PROVIDER_SIDE);
        Result result = mock(Result.class);
        RpcTraceScope scope = mock(RpcTraceScope.class);
        when(invoker.invoke(invocation)).thenReturn(result);
        when(invocation.getMethodName()).thenReturn("m1");
        when(invocation.getObjectAttachments()).thenReturn(Map.of("x-trace-id", "upstream-trace"));
        when(tracerBridge.extractScope(any(), any())).thenReturn(scope);

        Result actual = filter.invoke(invoker, invocation);

        assertSame(result, actual);
        InOrder inOrder = inOrder(tracerBridge, hook, invoker, scope);
        inOrder.verify(tracerBridge).extractScope(any(), eq(Map.of("x-trace-id", "upstream-trace")));
        inOrder.verify(hook).before(any());
        inOrder.verify(invoker).invoke(invocation);
        inOrder.verify(scope).close();
    }

    @Test
    void shouldBypassWhenDisabled() {
        properties.setEnabled(false);
        Invocation invocation = mock(Invocation.class);
        Invoker<?> invoker = mockInvoker();
        Result result = mock(Result.class);
        when(invoker.invoke(invocation)).thenReturn(result);
        when(invocation.getMethodName()).thenReturn("m1");
        when(invocation.getObjectAttachments()).thenReturn(Map.of());

        Result actual = filter.invoke(invoker, invocation);

        assertSame(result, actual);
        verifyNoInteractions(hook);
    }

    @Test
    void shouldBypassWhenSpringNotInitialized() {
        RpcDubboSupportHolder.set(null, null, null);
        Invocation invocation = mock(Invocation.class);
        Invoker<?> invoker = mockInvoker();
        Result result = mock(Result.class);
        when(invoker.invoke(invocation)).thenReturn(result);
        when(invocation.getMethodName()).thenReturn("m1");

        Result actual = filter.invoke(invoker, invocation);

        assertSame(result, actual);
        verifyNoInteractions(hook, tracerBridge);
    }

    @Test
    void shouldConvertAttachmentsAndCallOnError() {
        Invocation invocation = mock(Invocation.class);
        Invoker<?> invoker = mockInvoker();
        when(invocation.getMethodName()).thenReturn("m1");
        Map<String, Object> attachments = new HashMap<>();
        attachments.put("k1", 123);
        when(invocation.getObjectAttachments()).thenReturn(attachments);
        RuntimeException ex = new RuntimeException("boom");
        when(invoker.invoke(invocation)).thenThrow(ex);

        RuntimeException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class, () -> filter.invoke(invoker, invocation));

        assertSame(ex, thrown);
        ArgumentCaptor<RpcCallContext> ctxCaptor = ArgumentCaptor.forClass(RpcCallContext.class);
        verify(hook).before(ctxCaptor.capture());
        verify(hook).onError(any(), any(RpcCallResult.class));
        verify(hook).cleanup(any());
        verify(tracerBridge).inject(any());
        org.junit.jupiter.api.Assertions.assertEquals("123", ctxCaptor.getValue().getMetadata().getAttachments().get("k1"));
    }

    @Test
    void shouldCompleteFailureLifecycleWhenInvokerThrowsError() {
        Invocation invocation = mock(Invocation.class);
        Invoker<?> invoker = mockInvoker();
        AssertionError primary = new AssertionError("fatal");
        when(invocation.getMethodName()).thenReturn("m1");
        when(invocation.getObjectAttachments()).thenReturn(Map.of());
        when(invoker.invoke(invocation)).thenThrow(primary);
        when(tracerBridge.inject(any())).thenReturn(Map.of());

        AssertionError thrown = org.junit.jupiter.api.Assertions.assertThrows(
                AssertionError.class, () -> filter.invoke(invoker, invocation));

        assertSame(primary, thrown);
        verify(hook).before(any());
        verify(hook).onError(any(), any(RpcCallResult.class));
        verify(hook).cleanup(any());
    }

    @Test
    void shouldExtractProviderContextAndCleanUpWhenInvocationFails() {
        Invocation invocation = mock(Invocation.class);
        Invoker<?> invoker = mockInvoker(CommonConstants.PROVIDER_SIDE);
        RuntimeException ex = new RuntimeException("boom");
        when(invocation.getMethodName()).thenReturn("m1");
        when(invocation.getObjectAttachments()).thenReturn(Map.of("x-trace-id", "upstream-trace"));
        when(invoker.invoke(invocation)).thenThrow(ex);

        RuntimeException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class, () -> filter.invoke(invoker, invocation));

        assertSame(ex, thrown);
        InOrder inOrder = inOrder(tracerBridge, hook, invoker);
        inOrder.verify(tracerBridge).extractScope(any(), eq(Map.of("x-trace-id", "upstream-trace")));
        inOrder.verify(hook).before(any());
        inOrder.verify(invoker).invoke(invocation);
        inOrder.verify(hook).onError(any(), any(RpcCallResult.class));
        inOrder.verify(hook).cleanup(any());
        verify(tracerBridge, never()).inject(any());
        verify(invocation, never()).setAttachment(anyString(), anyString());
    }

    @Test
    void shouldReportResultContainedExceptionAsFailure() {
        Invocation invocation = mock(Invocation.class);
        Invoker<?> invoker = mockInvoker();
        RuntimeException ex = new RuntimeException("business failure");
        Result result = new AppResponse(ex);
        when(invoker.invoke(invocation)).thenReturn(result);
        when(invocation.getMethodName()).thenReturn("m1");
        when(invocation.getObjectAttachments()).thenReturn(Map.of());

        Result actual = filter.invoke(invoker, invocation);

        assertSame(result, actual);
        ArgumentCaptor<RpcCallResult> resultCaptor = ArgumentCaptor.forClass(RpcCallResult.class);
        verify(hook).onError(any(), resultCaptor.capture());
        verify(hook).cleanup(any());
        verify(hook, never()).after(any(), any());
        org.junit.jupiter.api.Assertions.assertFalse(resultCaptor.getValue().isSuccess());
        assertSame(ex, resultCaptor.getValue().getError().orElseThrow());
    }

    @Test
    void shouldCompleteHooksOnlyAfterAsyncResultCompletes() {
        Invocation invocation = new RpcInvocation();
        ((RpcInvocation) invocation).setMethodName("m1");
        Invoker<?> invoker = mockInvoker();
        CompletableFuture<AppResponse> responseFuture = new CompletableFuture<>();
        Result result = new AsyncRpcResult(responseFuture, invocation);
        when(invoker.invoke(invocation)).thenReturn(result);

        Result actual = filter.invoke(invoker, invocation);

        assertSame(result, actual);
        verify(hook).before(any());
        verify(hook, never()).after(any(), any());
        verify(hook, never()).onError(any(), any());
        verify(hook, never()).cleanup(any());

        responseFuture.complete(new AppResponse("ok"));

        verify(hook).after(any(), any(RpcCallResult.class));
        verify(hook).cleanup(any());
        verify(hook, never()).onError(any(), any());
    }

    @Test
    void shouldCloseProviderTraceScopeBeforeAsyncResultCompletes() {
        Invocation invocation = new RpcInvocation();
        ((RpcInvocation) invocation).setMethodName("m1");
        ((RpcInvocation) invocation).setObjectAttachments(Map.of("x-trace-id", "upstream-trace"));
        Invoker<?> invoker = mockInvoker(CommonConstants.PROVIDER_SIDE);
        RpcTraceScope scope = mock(RpcTraceScope.class);
        CompletableFuture<AppResponse> responseFuture = new CompletableFuture<>();
        Result result = new AsyncRpcResult(responseFuture, invocation);
        when(invoker.invoke(invocation)).thenReturn(result);
        when(tracerBridge.extractScope(any(), eq(Map.of("x-trace-id", "upstream-trace"))))
                .thenReturn(scope);

        Result actual = filter.invoke(invoker, invocation);

        assertSame(result, actual);
        verify(scope, times(1)).close();
        verify(hook).before(any());
        verify(hook, never()).after(any(), any());
        verify(hook, never()).onError(any(), any());
        verify(hook, never()).cleanup(any());

        responseFuture.complete(new AppResponse("ok"));

        verify(scope, times(1)).close();
        verify(hook, times(1)).after(any(), any(RpcCallResult.class));
        verify(hook, never()).onError(any(), any());
        verify(hook, times(1)).cleanup(any());
    }

    @Test
    void shouldReportAsyncResultContainedExceptionAfterCompletion() {
        Invocation invocation = new RpcInvocation();
        ((RpcInvocation) invocation).setMethodName("m1");
        Invoker<?> invoker = mockInvoker();
        CompletableFuture<AppResponse> responseFuture = new CompletableFuture<>();
        Result result = new AsyncRpcResult(responseFuture, invocation);
        RuntimeException ex = new RuntimeException("async business failure");
        when(invoker.invoke(invocation)).thenReturn(result);

        filter.invoke(invoker, invocation);

        verify(hook, never()).onError(any(), any());
        verify(hook, never()).cleanup(any());

        responseFuture.complete(new AppResponse(ex));

        ArgumentCaptor<RpcCallResult> resultCaptor = ArgumentCaptor.forClass(RpcCallResult.class);
        verify(hook).onError(any(), resultCaptor.capture());
        verify(hook).cleanup(any());
        verify(hook, never()).after(any(), any());
        org.junit.jupiter.api.Assertions.assertFalse(resultCaptor.getValue().isSuccess());
        assertSame(ex, resultCaptor.getValue().getError().orElseThrow());
    }

    @Test
    void shouldNotInvokeHooksWhenProviderExtractionFailsBeforeInvocationOpens() {
        Invocation invocation = mock(Invocation.class);
        Invoker<?> invoker = mockInvoker(CommonConstants.PROVIDER_SIDE);
        RuntimeException ex = new RuntimeException("extract failure");
        when(invocation.getMethodName()).thenReturn("m1");
        when(invocation.getObjectAttachments()).thenReturn(Map.of("x-trace-id", "upstream-trace"));
        doThrow(ex).when(tracerBridge).extractScope(any(), any());

        RuntimeException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class, () -> filter.invoke(invoker, invocation));

        assertSame(ex, thrown);
        verifyNoInteractions(hook);
        verify(invoker, never()).invoke(any());
    }

    @Test
    void shouldNotInjectWhenContextPropagationDisabled() {
        properties.setContextPropagationEnabled(false);
        Invocation invocation = mock(Invocation.class);
        Invoker<?> invoker = mockInvoker();
        Result result = mock(Result.class);
        when(invoker.invoke(invocation)).thenReturn(result);
        when(invocation.getMethodName()).thenReturn("m1");
        when(invocation.getObjectAttachments()).thenReturn(Map.of());

        filter.invoke(invoker, invocation);

        verifyNoInteractions(tracerBridge);
        verify(hook).before(any());
        verify(hook).after(any(), any(RpcCallResult.class));
        verify(hook).cleanup(any());
    }

    @Test
    void shouldHandleNullAttachments() {
        Invocation invocation = mock(Invocation.class);
        Invoker<?> invoker = mockInvoker();
        Result result = mock(Result.class);
        when(invoker.invoke(invocation)).thenReturn(result);
        when(invocation.getMethodName()).thenReturn("m1");
        when(invocation.getObjectAttachments()).thenReturn(null);
        when(tracerBridge.inject(any())).thenReturn(Map.of("x-trace-id", "t1"));

        Result actual = filter.invoke(invoker, invocation);

        assertSame(result, actual);
        verify(hook).before(any());
        verify(hook).after(any(), any(RpcCallResult.class));
        verify(hook).cleanup(any());
    }

    @Test
    void shouldHandleMixedAttachmentTypes() {
        Invocation invocation = mock(Invocation.class);
        Invoker<?> invoker = mockInvoker();
        Result result = mock(Result.class);
        when(invoker.invoke(invocation)).thenReturn(result);
        when(invocation.getMethodName()).thenReturn("m1");
        Map<String, Object> attachments = new HashMap<>();
        attachments.put("k1", "string");
        attachments.put("k2", 123);
        attachments.put("k3", true);
        when(invocation.getObjectAttachments()).thenReturn(attachments);
        when(tracerBridge.inject(any())).thenReturn(Map.of("x-trace-id", "t1"));

        Result actual = filter.invoke(invoker, invocation);

        assertSame(result, actual);
        verify(hook).before(any());
        verify(hook).after(any(), any(RpcCallResult.class));
        verify(hook).cleanup(any());
    }

    @Test
    void shouldHandleEmptyTracerInjectResult() {
        Invocation invocation = mock(Invocation.class);
        Invoker<?> invoker = mockInvoker();
        Result result = mock(Result.class);
        when(invoker.invoke(invocation)).thenReturn(result);
        when(invocation.getMethodName()).thenReturn("m1");
        when(invocation.getObjectAttachments()).thenReturn(Map.of());
        when(tracerBridge.inject(any())).thenReturn(null);

        Result actual = filter.invoke(invoker, invocation);

        assertSame(result, actual);
        verify(hook).before(any());
        verify(hook).after(any(), any(RpcCallResult.class));
        verify(hook).cleanup(any());
        verify(tracerBridge).inject(any());
        verify(invocation, never()).setAttachment(anyString(), anyString());
    }

    @Test
    void shouldHandleEmptyMapTracerInjectResult() {
        Invocation invocation = mock(Invocation.class);
        Invoker<?> invoker = mockInvoker();
        Result result = mock(Result.class);
        when(invoker.invoke(invocation)).thenReturn(result);
        when(invocation.getMethodName()).thenReturn("m1");
        when(invocation.getObjectAttachments()).thenReturn(Map.of());
        when(tracerBridge.inject(any())).thenReturn(Map.of());

        Result actual = filter.invoke(invoker, invocation);

        assertSame(result, actual);
        verify(hook).before(any());
        verify(hook).after(any(), any(RpcCallResult.class));
        verify(hook).cleanup(any());
        verify(tracerBridge).inject(any());
        verify(invocation, never()).setAttachment(anyString(), anyString());
    }

    @Test
    void shouldHandlePartialNullInSupportHolder() {
        RpcDubboSupportHolder.set(hookChain, null, properties);
        Invocation invocation = mock(Invocation.class);
        Invoker<?> invoker = mockInvoker();
        Result result = mock(Result.class);
        when(invoker.invoke(invocation)).thenReturn(result);
        when(invocation.getMethodName()).thenReturn("m1");
        when(invocation.getObjectAttachments()).thenReturn(Map.of());

        Result actual = filter.invoke(invoker, invocation);

        assertSame(result, actual);
        verifyNoInteractions(hook, tracerBridge);
    }

    @Test
    void shouldHandleNullPropertiesInSupportHolder() {
        RpcDubboSupportHolder.set(hookChain, tracerBridge, null);
        Invocation invocation = mock(Invocation.class);
        Invoker<?> invoker = mockInvoker();
        Result result = mock(Result.class);
        when(invoker.invoke(invocation)).thenReturn(result);
        when(invocation.getMethodName()).thenReturn("m1");
        when(invocation.getObjectAttachments()).thenReturn(Map.of());

        Result actual = filter.invoke(invoker, invocation);

        assertSame(result, actual);
        verifyNoInteractions(hook, tracerBridge);
    }

    @Test
    void shouldHandleNullHookChainInSupportHolder() {
        RpcDubboSupportHolder.set(null, tracerBridge, properties);
        Invocation invocation = mock(Invocation.class);
        Invoker<?> invoker = mockInvoker();
        Result result = mock(Result.class);
        when(invoker.invoke(invocation)).thenReturn(result);
        when(invocation.getMethodName()).thenReturn("m1");
        when(invocation.getObjectAttachments()).thenReturn(Map.of());

        Result actual = filter.invoke(invoker, invocation);

        assertSame(result, actual);
        verifyNoInteractions(hook, tracerBridge);
    }

    @Test
    void shouldHandleMultipleAttachments() {
        Invocation invocation = mock(Invocation.class);
        Invoker<?> invoker = mockInvoker();
        Result result = mock(Result.class);
        when(invoker.invoke(invocation)).thenReturn(result);
        when(invocation.getMethodName()).thenReturn("m1");
        Map<String, Object> attachments = new HashMap<>();
        attachments.put("k1", "v1");
        attachments.put("k2", 123);
        attachments.put("k3", true);
        when(invocation.getObjectAttachments()).thenReturn(attachments);
        when(tracerBridge.inject(any())).thenReturn(Map.of("x-trace-id", "t1"));

        Result actual = filter.invoke(invoker, invocation);

        assertSame(result, actual);
        ArgumentCaptor<RpcCallContext> ctxCaptor = ArgumentCaptor.forClass(RpcCallContext.class);
        verify(hook).before(ctxCaptor.capture());
        verify(hook).after(any(), any(RpcCallResult.class));
        verify(hook).cleanup(any());
        Map<String, String> metadataAttachments = ctxCaptor.getValue().getMetadata().getAttachments();
        org.junit.jupiter.api.Assertions.assertEquals("v1", metadataAttachments.get("k1"));
        org.junit.jupiter.api.Assertions.assertEquals("123", metadataAttachments.get("k2"));
        org.junit.jupiter.api.Assertions.assertEquals("true", metadataAttachments.get("k3"));
    }

    @Test
    void shouldNotInvokeBusinessWhenBeforeFailsAndShouldCleanUp() {
        RuntimeException beforeFailure = new RuntimeException("before failure");
        java.util.concurrent.atomic.AtomicInteger cleanupCalls = new java.util.concurrent.atomic.AtomicInteger();
        RpcHook failingHook = new RpcHook() {
            @Override
            public void before(RpcCallContext context) {
                throw beforeFailure;
            }

            @Override
            public void cleanup(RpcCallContext context) {
                cleanupCalls.incrementAndGet();
            }
        };
        RpcDubboSupportHolder.set(new RpcHookChain(List.of(failingHook)), tracerBridge, properties);
        Invocation invocation = mock(Invocation.class);
        Invoker<?> invoker = mockInvoker();
        when(invocation.getMethodName()).thenReturn("m1");
        when(invocation.getObjectAttachments()).thenReturn(Map.of());
        when(tracerBridge.inject(any())).thenReturn(Map.of());

        RuntimeException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class, () -> filter.invoke(invoker, invocation));

        assertSame(beforeFailure, thrown);
        verify(invoker, never()).invoke(any());
        org.junit.jupiter.api.Assertions.assertEquals(1, cleanupCalls.get());
    }

    private Invoker<?> mockInvoker() {
        return mockInvoker(CommonConstants.CONSUMER_SIDE);
    }

    private Invoker<?> mockInvoker(String side) {
        Invoker<?> invoker = mock(Invoker.class);
        URL url = URL.valueOf("dubbo://localhost:20880/com.foo.BarService?side=" + side);
        when(invoker.getUrl()).thenReturn(url);
        when(invoker.getInterface()).thenReturn((Class) Object.class);
        return invoker;
    }
}
