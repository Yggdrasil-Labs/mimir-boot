package com.yggdrasil.labs.rpc.feign.client;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.yggdrasil.labs.rpc.core.context.RpcCallContext;
import com.yggdrasil.labs.rpc.core.context.RpcCallMetadata;
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
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

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
    void shouldCompleteFailureLifecycleWhenDelegateThrowsError() throws Exception {
        Request request = Request.create(
                Request.HttpMethod.GET, "http://example.com/api", Map.of(), null, StandardCharsets.UTF_8, null);
        AssertionError primary = new AssertionError("fatal");
        when(delegate.execute(any(), any())).thenThrow(primary);
        when(tracerBridge.inject(any())).thenReturn(Map.of());

        AssertionError thrown = Assertions.assertThrows(
                AssertionError.class, () -> client.execute(request, new Request.Options()));

        assertSame(primary, thrown);
        verify(hook).before(any());
        verify(hook).onError(any(), any(RpcCallResult.class));
        verify(hook).cleanup(any());
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
    void shouldExcludeSensitiveHeadersFromMetadataWithoutChangingRequest() throws Exception {
        Map<String, Collection<String>> headers = Map.of(
                "Authorization", List.of("Bearer secret-token"),
                "cOoKiE", List.of("session=secret"),
                "x-request-id", List.of("request-1"));
        Request request = Request.create(
                Request.HttpMethod.GET, "http://example.com/api", headers, null, StandardCharsets.UTF_8, null);
        Response response = Response.builder()
                .request(request)
                .status(200)
                .reason("OK")
                .headers(Map.of())
                .build();
        when(delegate.execute(any(), any())).thenReturn(response);
        when(tracerBridge.inject(any())).thenReturn(Map.of());

        client.execute(request, new Request.Options());

        ArgumentCaptor<RpcCallContext> contextCaptor = ArgumentCaptor.forClass(RpcCallContext.class);
        verify(hook).before(contextCaptor.capture());
        Map<String, String> attachments = contextCaptor.getValue().getMetadata().getAttachments();
        Assertions.assertFalse(attachments.containsKey("Authorization"));
        Assertions.assertFalse(attachments.containsKey("cOoKiE"));
        Assertions.assertEquals("request-1", attachments.get("x-request-id"));
        verify(delegate).execute(same(request), any());
    }

    @Test
    void shouldExposeOnlySafeHeadersInMetadataRegardlessOfCaseOrValueCount() throws Exception {
        Map<String, Collection<String>> headers = Map.of(
                "pRoXy-AuThOrIzAtIoN", List.of("Basic proxy-secret"),
                "X-Api-Key", List.of("key-1", "key-2"),
                "x-AuTh-ToKeN", List.of("token-1", "token-2"),
                "x-unknown-header", List.of("must-not-be-exposed"),
                "X-Request-Id", List.of("request-1", "request-2"),
                "Content-Type", List.of("application/json"));
        Request request = Request.create(
                Request.HttpMethod.GET, "http://example.com/api", headers, null, StandardCharsets.UTF_8, null);
        Response response = Response.builder()
                .request(request)
                .status(200)
                .reason("OK")
                .headers(Map.of())
                .build();
        when(delegate.execute(any(), any())).thenReturn(response);
        when(tracerBridge.inject(any())).thenReturn(Map.of());

        client.execute(request, new Request.Options());

        ArgumentCaptor<RpcCallContext> contextCaptor = ArgumentCaptor.forClass(RpcCallContext.class);
        verify(hook).before(contextCaptor.capture());
        Map<String, String> attachments = contextCaptor.getValue().getMetadata().getAttachments();
        Assertions.assertEquals("request-1,request-2", attachments.get("X-Request-Id"));
        Assertions.assertEquals("application/json", attachments.get("Content-Type"));
        Assertions.assertFalse(attachments.containsKey("pRoXy-AuThOrIzAtIoN"));
        Assertions.assertFalse(attachments.containsKey("X-Api-Key"));
        Assertions.assertFalse(attachments.containsKey("x-AuTh-ToKeN"));
        Assertions.assertFalse(attachments.containsKey("x-unknown-header"));
        Assertions.assertFalse(attachments.containsValue("Basic proxy-secret"));
        Assertions.assertFalse(attachments.containsValue("key-1,key-2"));
        Assertions.assertFalse(attachments.containsValue("token-1,token-2"));
    }

    @Test
    void shouldKeepAllSafeHeaderValuesInIterationOrder() throws Exception {
        Request request = Request.create(
                Request.HttpMethod.GET,
                "http://example.com/api",
                Map.of("Content-Type", List.of("a", "b"), "Authorization", List.of("secret")),
                null,
                StandardCharsets.UTF_8,
                null);
        Response response = Response.builder()
                .request(request)
                .status(200)
                .reason("OK")
                .headers(Map.of())
                .build();
        when(delegate.execute(any(), any())).thenReturn(response);
        when(tracerBridge.inject(any())).thenReturn(Map.of());

        client.execute(request, new Request.Options());

        ArgumentCaptor<RpcCallContext> contextCaptor = ArgumentCaptor.forClass(RpcCallContext.class);
        verify(hook).before(contextCaptor.capture());
        Assertions.assertEquals("a,b", contextCaptor.getValue().getMetadata().getAttachments().get("Content-Type"));
        Assertions.assertFalse(contextCaptor.getValue().getMetadata().getAttachments().containsKey("Authorization"));
    }

    @Test
    void shouldSanitizeMetadataForAllUrlShapes() throws Exception {
        assertMetadata(
                request("https://api.example.test:8443/orders?token=secret#detail"),
                "api.example.test",
                "api.example.test:8443");
        assertMetadata(
                request("https://user:password@api.example.test:8443/orders"),
                "api.example.test",
                "api.example.test:8443");
        assertMetadata(request("/orders?token=secret"), "[unknown-service]", "/orders");
        assertMetadata(
                request("https:/orders?token=secret"), "[unknown-service]", "[invalid-authority]");
        assertMetadata(
                request("mailto:user:password@example.test"), "[unknown-service]", "[opaque-url]");
        assertMetadata(request("http://[bad"), "[unknown-service]", "[invalid-url]");
    }

    @Test
    void shouldUseMissingUrlPlaceholdersAndDelegateOriginalRequest() throws Exception {
        properties.setContextPropagationEnabled(false);
        Request request = mock(Request.class);
        Request.Options options = mock(Request.Options.class);
        when(request.url()).thenReturn(null);
        when(request.httpMethod()).thenReturn(Request.HttpMethod.GET);
        when(request.headers()).thenReturn(Map.of());
        when(delegate.execute(same(request), same(options))).thenReturn(response(request));

        RpcCallMetadata metadata = metadataFor(request, options);

        Assertions.assertEquals("[unknown-service]", metadata.getService());
        Assertions.assertEquals("[missing-url]", metadata.getTarget());
        Assertions.assertNull(metadata.getProtocol());
        verify(delegate).execute(same(request), same(options));
    }

    @Test
    void shouldDelegateOriginalRequestWhenUrlMissingAndContextIsInjected() throws Exception {
        Request request = mock(Request.class);
        Request.Options options = mock(Request.Options.class);
        when(request.url()).thenReturn(null);
        when(request.httpMethod()).thenReturn(Request.HttpMethod.GET);
        when(request.headers()).thenReturn(Map.of());
        when(tracerBridge.inject(any())).thenReturn(Map.of("trace-id", "t1"));
        Response response = response(request);
        when(delegate.execute(same(request), same(options))).thenReturn(response);

        Response actual = client.execute(request, options);

        assertSame(response, actual);
        verify(tracerBridge).inject(any());
        verify(delegate).execute(same(request), same(options));
    }

    @Test
    void shouldOnlyLogSanitizedUrlsAcrossEnabledDisabledAndFailureBranches() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(RpcFeignClient.class);
        Level previousLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(Level.DEBUG);
        logger.addAppender(appender);
        try {
            when(tracerBridge.inject(any())).thenReturn(Map.of());
            Request credentialed = request("https://user:password@api.example.test:8443/orders?token=secret#detail");
            when(delegate.execute(same(credentialed), any())).thenReturn(response(credentialed));
            client.execute(credentialed, new Request.Options());

            properties.setEnabled(false);
            Request relative = request("/orders?token=secret");
            when(delegate.execute(same(relative), any())).thenReturn(response(relative));
            client.execute(relative, new Request.Options());

            properties.setEnabled(true);
            Request opaque = request("mailto:user:password@example.test");
            when(delegate.execute(same(opaque), any())).thenThrow(new IOException("delegate failed"));
            Assertions.assertThrows(IOException.class, () -> client.execute(opaque, new Request.Options()));

            String output = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .collect(Collectors.joining("\n"));
            Assertions.assertTrue(output.contains("https://api.example.test:8443/orders"));
            Assertions.assertTrue(output.contains("/orders"));
            Assertions.assertTrue(output.contains("[opaque-url]"));
            Assertions.assertFalse(output.contains("user:password"));
            Assertions.assertFalse(output.contains("token=secret"));
            Assertions.assertFalse(output.contains("#detail"));
            Assertions.assertFalse(output.contains("mailto:user:password@example.test"));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }
    }

    @Test
    void shouldNotAttachRawFailureThrowableToDebugLogEvent() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(RpcFeignClient.class);
        Level previousLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(Level.DEBUG);
        logger.addAppender(appender);
        try {
            properties.setContextPropagationEnabled(false);
            Request request = request("https://user:password@api.example.test:8443/orders?token=secret");
            IllegalStateException failure = new IllegalStateException(
                    "request failed: https://user:password@api.example.test:8443/orders?token=secret");
            when(delegate.execute(same(request), any())).thenThrow(failure);
            Request.Options options = new Request.Options();

            Assertions.assertThrows(IllegalStateException.class, () -> client.execute(request, options));

            ILoggingEvent event = appender.list.stream()
                    .filter(loggingEvent -> loggingEvent.getFormattedMessage().contains("HTTP call failed"))
                    .findFirst()
                    .orElseThrow();
            Assertions.assertNull(event.getThrowableProxy());
            Assertions.assertTrue(event.getFormattedMessage().contains("error=IllegalStateException"));
            Assertions.assertTrue(event.getFormattedMessage().contains("https://api.example.test:8443/orders"));
            Assertions.assertFalse(event.getFormattedMessage().contains("user:password"));
            Assertions.assertFalse(event.getFormattedMessage().contains("token=secret"));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }
    }

    private void assertMetadata(Request request, String service, String target) throws Exception {
        RpcCallMetadata metadata = metadataFor(request, new Request.Options());

        Assertions.assertEquals(service, metadata.getService());
        Assertions.assertEquals(target, metadata.getTarget());
    }

    private RpcCallMetadata metadataFor(Request request, Request.Options options) throws Exception {
        clearInvocations(hook, delegate, tracerBridge);
        when(tracerBridge.inject(any())).thenReturn(Map.of());
        when(delegate.execute(same(request), same(options))).thenReturn(response(request));

        client.execute(request, options);

        ArgumentCaptor<RpcCallContext> contextCaptor = ArgumentCaptor.forClass(RpcCallContext.class);
        verify(hook).before(contextCaptor.capture());
        return contextCaptor.getValue().getMetadata();
    }

    private Request request(String url) {
        return Request.create(Request.HttpMethod.GET, url, Map.of(), null, StandardCharsets.UTF_8, null);
    }

    private Response response(Request request) {
        return Response.builder().request(request).status(200).reason("OK").headers(Map.of()).build();
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

    @Test
    void shouldNotCallDelegateWhenBeforeFailsAndShouldCleanUp() {
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
        client = new RpcFeignClient(delegate, new RpcHookChain(List.of(failingHook)), tracerBridge, properties);
        Request request = Request.create(
                Request.HttpMethod.GET, "http://example.com/api", Map.of(), null, StandardCharsets.UTF_8, null);

        RuntimeException thrown = Assertions.assertThrows(
                RuntimeException.class, () -> client.execute(request, new Request.Options()));

        assertSame(beforeFailure, thrown);
        verifyNoInteractions(delegate);
        Assertions.assertEquals(1, cleanupCalls.get());
    }
}
