package com.yggdrasil.labs.rpc.dubbo.filter;

import com.yggdrasil.labs.rpc.core.context.RpcCallContext;
import com.yggdrasil.labs.rpc.core.context.RpcCallResult;
import com.yggdrasil.labs.rpc.core.hook.RpcHook;
import com.yggdrasil.labs.rpc.core.hook.RpcHookChain;
import com.yggdrasil.labs.rpc.core.tracing.RpcTracerBridge;
import com.yggdrasil.labs.rpc.dubbo.config.DubboProperties;
import com.yggdrasil.labs.rpc.dubbo.support.RpcDubboSupportHolder;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private Invoker<?> mockInvoker() {
        Invoker<?> invoker = mock(Invoker.class);
        URL url = URL.valueOf("dubbo://localhost:20880/com.foo.BarService");
        when(invoker.getUrl()).thenReturn(url);
        when(invoker.getInterface()).thenReturn((Class) Object.class);
        return invoker;
    }
}

