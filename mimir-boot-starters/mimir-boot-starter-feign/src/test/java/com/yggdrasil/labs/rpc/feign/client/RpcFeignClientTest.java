package com.yggdrasil.labs.rpc.feign.client;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

import com.yggdrasil.labs.rpc.core.context.RpcCallResult;
import com.yggdrasil.labs.rpc.core.hook.RpcHook;
import com.yggdrasil.labs.rpc.core.hook.RpcHookChain;
import com.yggdrasil.labs.rpc.core.tracing.RpcTracerBridge;
import com.yggdrasil.labs.rpc.feign.config.FeignProperties;
import feign.Client;
import feign.Request;
import feign.Response;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RpcFeignClientTest {

    private final Client delegate = mock(Client.class);
    private final RpcHook hook = mock(RpcHook.class);
    private final RpcHookChain hookChain = new RpcHookChain(List.of(hook));
    private final RpcTracerBridge tracerBridge = mock(RpcTracerBridge.class);
    private final FeignProperties properties = new FeignProperties();
    private RpcFeignClient client;

    @BeforeEach
    void setUp() {
        properties.setEnabled(true);
        properties.setContextPropagationEnabled(true);
        client = new RpcFeignClient(delegate, hookChain, tracerBridge, properties);
    }

    @Test
    void shouldInvokeHooksAndTracer() throws Exception {
        Request request = Request.create(
                Request.HttpMethod.GET, "http://example.com/api", Map.of(), null, StandardCharsets.UTF_8, null);
        Response response = Response.builder()
                .request(request)
                .status(200)
                .reason("OK")
                .headers(Map.of())
                .build();
        when(delegate.execute(any(), any())).thenReturn(response);
        when(tracerBridge.inject(any())).thenReturn(Map.of("x-trace-id", "t1"));

        Response actual = client.execute(request, new Request.Options());

        assertSame(response, actual);
        verify(hook).before(any());
        verify(hook).after(any(), any(RpcCallResult.class));
        verify(hook).cleanup(any());
        verify(tracerBridge).inject(any());
    }

    @Test
    void shouldBypassWhenDisabled() throws Exception {
        properties.setEnabled(false);
        Request request = Request.create(
                Request.HttpMethod.GET, "http://example.com/api", Map.of(), null, StandardCharsets.UTF_8, null);
        Response response = Response.builder()
                .request(request)
                .status(200)
                .reason("OK")
                .headers(Map.of())
                .build();
        when(delegate.execute(any(), any())).thenReturn(response);

        Response actual = client.execute(request, new Request.Options());

        assertSame(response, actual);
        verifyNoInteractions(hook);
    }

    @Test
    void shouldCallOnErrorAndCleanupOnIOException() throws Exception {
        properties.setContextPropagationEnabled(false);
        Request request = Request.create(
                Request.HttpMethod.POST,
                "http://example.com/api",
                Map.of(),
                "body".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8,
                null);
        IOException ioException = new IOException("fail");
        when(delegate.execute(any(), any())).thenThrow(ioException);

        IOException thrown = Assertions.assertThrows(
                IOException.class, () -> client.execute(request, new Request.Options()));

        assertSame(ioException, thrown);
        verify(hook).before(any());
        verify(hook).onError(any(), any(RpcCallResult.class));
        verify(hook).cleanup(any());
        verifyNoInteractions(tracerBridge);
    }

    @Test
    void shouldInjectHeadersWhenContextPropagationEnabled() throws Exception {
        Map<String, Collection<String>> headers = Map.of("h1", List.of("v1"));
        Request request = Request.create(
                Request.HttpMethod.GET, "http://example.com/api", headers, null, StandardCharsets.UTF_8, null);
        Response response = Response.builder()
                .request(request)
                .status(200)
                .reason("OK")
                .headers(Map.of())
                .build();
        when(delegate.execute(any(), any())).thenReturn(response);
        when(tracerBridge.inject(any())).thenReturn(Map.of("trace-id", "t1"));

        client.execute(request, new Request.Options());

        verify(delegate).execute(argThat(arg -> "t1".equals(arg.headers().get("trace-id").iterator().next())), any());
        verify(hook).before(any());
        verify(hook).after(any(), any(RpcCallResult.class));
        verify(hook).cleanup(any());
    }

    @Test
    void shouldHandleNullTracerInjectResult() throws Exception {
        Request request = Request.create(
                Request.HttpMethod.GET, "http://example.com/api", Map.of(), null, StandardCharsets.UTF_8, null);
        Response response = Response.builder()
                .request(request)
                .status(200)
                .reason("OK")
                .headers(Map.of())
                .build();
        when(delegate.execute(any(), any())).thenReturn(response);
        when(tracerBridge.inject(any())).thenReturn(null);

        Response actual = client.execute(request, new Request.Options());

        assertSame(response, actual);
        verify(hook).before(any());
        verify(hook).after(any(), any(RpcCallResult.class));
        verify(hook).cleanup(any());
        verify(tracerBridge).inject(any());
        verify(delegate).execute(eq(request), any());
    }

    @Test
    void shouldHandleEmptyMapTracerInjectResult() throws Exception {
        Request request = Request.create(
                Request.HttpMethod.GET, "http://example.com/api", Map.of(), null, StandardCharsets.UTF_8, null);
        Response response = Response.builder()
                .request(request)
                .status(200)
                .reason("OK")
                .headers(Map.of())
                .build();
        when(delegate.execute(any(), any())).thenReturn(response);
        when(tracerBridge.inject(any())).thenReturn(Map.of());

        Response actual = client.execute(request, new Request.Options());

        assertSame(response, actual);
        verify(hook).before(any());
        verify(hook).after(any(), any(RpcCallResult.class));
        verify(hook).cleanup(any());
        verify(tracerBridge).inject(any());
        verify(delegate).execute(eq(request), any());
    }

    @Test
    void shouldHandleEmptyHeaders() throws Exception {
        Request request = Request.create(
                Request.HttpMethod.GET, "http://example.com/api", Map.of(), null, StandardCharsets.UTF_8, null);
        Response response = Response.builder()
                .request(request)
                .status(200)
                .reason("OK")
                .headers(Map.of())
                .build();
        when(delegate.execute(any(), any())).thenReturn(response);
        when(tracerBridge.inject(any())).thenReturn(Map.of("trace-id", "t1"));

        Response actual = client.execute(request, new Request.Options());

        assertSame(response, actual);
        verify(hook).before(any());
        verify(hook).after(any(), any(RpcCallResult.class));
        verify(hook).cleanup(any());
    }

    @Test
    void shouldHandleHeadersWithEmptyCollections() throws Exception {
        Map<String, Collection<String>> headers = Map.of("h1", List.of());
        Request request = Request.create(
                Request.HttpMethod.GET, "http://example.com/api", headers, null, StandardCharsets.UTF_8, null);
        Response response = Response.builder()
                .request(request)
                .status(200)
                .reason("OK")
                .headers(Map.of())
                .build();
        when(delegate.execute(any(), any())).thenReturn(response);
        when(tracerBridge.inject(any())).thenReturn(Map.of("trace-id", "t1"));

        Response actual = client.execute(request, new Request.Options());

        assertSame(response, actual);
        verify(hook).before(any());
        verify(hook).after(any(), any(RpcCallResult.class));
        verify(hook).cleanup(any());
    }

    @Test
    void shouldCallOnErrorAndCleanupOnRuntimeException() throws Exception {
        Request request = Request.create(
                Request.HttpMethod.POST,
                "http://example.com/api",
                Map.of(),
                "body".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8,
                null);
        RuntimeException runtimeException = new RuntimeException("fail");
        when(delegate.execute(any(), any())).thenThrow(runtimeException);
        when(tracerBridge.inject(any())).thenReturn(Map.of("trace-id", "t1"));

        RuntimeException thrown = Assertions.assertThrows(
                RuntimeException.class, () -> client.execute(request, new Request.Options()));

        assertSame(runtimeException, thrown);
        verify(hook).before(any());
        verify(hook).onError(any(), any(RpcCallResult.class));
        verify(hook).cleanup(any());
        verify(tracerBridge).inject(any());
    }

    @Test
    void shouldHandleMultipleHeaders() throws Exception {
        Map<String, Collection<String>> headers = Map.of(
                "h1", List.of("v1"),
                "h2", List.of("v2"),
                "h3", List.of("v3"));
        Request request = Request.create(
                Request.HttpMethod.GET, "http://example.com/api", headers, null, StandardCharsets.UTF_8, null);
        Response response = Response.builder()
                .request(request)
                .status(200)
                .reason("OK")
                .headers(Map.of())
                .build();
        when(delegate.execute(any(), any())).thenReturn(response);
        when(tracerBridge.inject(any())).thenReturn(Map.of("trace-id", "t1", "span-id", "s1"));

        client.execute(request, new Request.Options());

        verify(delegate).execute(argThat(arg -> {
            Map<String, Collection<String>> newHeaders = arg.headers();
            return newHeaders.containsKey("trace-id") && newHeaders.containsKey("span-id");
        }), any());
        verify(hook).before(any());
        verify(hook).after(any(), any(RpcCallResult.class));
        verify(hook).cleanup(any());
    }

    @Test
    void shouldPreserveOriginalHeadersWhenInjecting() throws Exception {
        Map<String, Collection<String>> headers = Map.of("original", List.of("value"));
        Request request = Request.create(
                Request.HttpMethod.GET, "http://example.com/api", headers, null, StandardCharsets.UTF_8, null);
        Response response = Response.builder()
                .request(request)
                .status(200)
                .reason("OK")
                .headers(Map.of())
                .build();
        when(delegate.execute(any(), any())).thenReturn(response);
        when(tracerBridge.inject(any())).thenReturn(Map.of("trace-id", "t1"));

        client.execute(request, new Request.Options());

        verify(delegate).execute(argThat(arg -> {
            Map<String, Collection<String>> newHeaders = arg.headers();
            return newHeaders.containsKey("original") && newHeaders.containsKey("trace-id");
        }), any());
        verify(hook).before(any());
        verify(hook).after(any(), any(RpcCallResult.class));
        verify(hook).cleanup(any());
    }
}

