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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
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
}

