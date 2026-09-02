package com.yggdrasil.labs.log.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.yggdrasil.labs.test.base.BaseUnitTest;
import com.yggdrasil.labs.test.util.AssertUtils;
import com.yggdrasil.labs.test.util.FilterChainMockBuilder;
import com.yggdrasil.labs.test.util.LogTestUtils;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * 访问日志过滤器测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@SuppressWarnings("deprecation")
class AccessLogFilterTest extends BaseUnitTest {

    private AccessLogFilter filter;
    private ListAppender<ILoggingEvent> listAppender;
    private Logger accessLogger;

    @Override
    @BeforeEach
    public void setUp() {
        super.setUp();
        filter = new AccessLogFilter(3000, null);
        listAppender = LogTestUtils.setupLogger("access.log");
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        accessLogger = context.getLogger("access.log");
    }

    @Override
    @AfterEach
    public void tearDown() {
        LogTestUtils.cleanupLogger(accessLogger, listAppender);
        super.tearDown();
    }

    /**
     * 测试正常请求（2xx）
     */
    @Test
    void testSuccessRequest() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/user/123");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = FilterChainMockBuilder.create()
                .statusCode(200)
                .build();

        filter.doFilter(request, response, chain);

        AssertUtils.assertLogSize(listAppender, 1);
        ILoggingEvent event = listAppender.list.get(0);
        String message = event.getFormattedMessage();
        assertTrue(message.contains("Outcome=[COMPLETED]"));
        assertTrue(message.contains("ErrorType=[-]"));
        assertTrue(message.matches(".*Duration=\\[[0-9]+ms\\].*"),
                "访问日志应包含毫秒耗时字段: " + message);
        AssertUtils.assertLogLevel(event, Level.INFO);
        AssertUtils.assertLogStatus(event, 200);
        AssertUtils.assertLogContains(event, "GET");
        AssertUtils.assertLogContains(event, "/api/user/123");
    }

    @Test
    void logsSynchronousException() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/failure");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("User-Agent")).thenReturn("JUnit");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(500);
        FilterChain chain = mock(FilterChain.class);
        IllegalStateException failure = new IllegalStateException("boom");
        doThrow(failure).when(chain).doFilter(any(), any());

        assertThrows(IllegalStateException.class, () -> filter.doFilter(request, response, chain));

        AssertUtils.assertLogSize(listAppender, 1);
        String message = listAppender.list.get(0).getFormattedMessage();
        assertTrue(message.contains("Status=[500]"));
        assertTrue(message.contains("Outcome=[ERROR]"));
        assertTrue(message.contains("ErrorType=[java.lang.IllegalStateException]"));
    }

    @Test
    void logsSynchronousErrorAsFailureAndPropagatesSameError() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/error");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("User-Agent")).thenReturn("JUnit");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        AssertionError failure = new AssertionError("boom");
        doThrow(failure).when(chain).doFilter(any(), any());

        AssertionError propagated = assertThrows(AssertionError.class,
                () -> filter.doFilter(request, response, chain));

        assertSame(failure, propagated);
        AssertUtils.assertLogSize(listAppender, 1);
        String message = listAppender.list.get(0).getFormattedMessage();
        assertTrue(message.contains("Outcome=[ERROR]"));
        assertTrue(message.contains("ErrorType=[java.lang.AssertionError]"));
    }


    @Test
    void defersUntilAsyncComplete() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/async");
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("User-Agent")).thenReturn("JUnit");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        when(request.isAsyncStarted()).thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AsyncContext asyncContext = mock(AsyncContext.class);
        when(request.getAsyncContext()).thenReturn(asyncContext);
        FilterChain chain = mock(FilterChain.class);
        doAnswer(invocation -> {
            ((HttpServletResponse) invocation.getArgument(1)).setStatus(201);
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        ArgumentCaptor<AsyncListener> listenerCaptor = ArgumentCaptor.forClass(AsyncListener.class);
        verify(asyncContext).addListener(listenerCaptor.capture());
        assertEquals(0, listAppender.list.size());
        listenerCaptor.getValue().onComplete(new AsyncEvent(asyncContext));

        AssertUtils.assertLogSize(listAppender, 1);
        String message = listAppender.list.get(0).getFormattedMessage();
        assertTrue(message.contains("Status=[201]"));
        assertTrue(message.contains("Outcome=[COMPLETED]"));
    }

    @Test
    void usesAsyncEventResponseForTerminalStatus() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/async-wrapper");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("User-Agent")).thenReturn("JUnit");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        when(request.isAsyncStarted()).thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        AsyncContext asyncContext = mock(AsyncContext.class);
        when(request.getAsyncContext()).thenReturn(asyncContext);
        HttpServletResponse asyncResponse = mock(HttpServletResponse.class);
        when(asyncResponse.getStatus()).thenReturn(202);
        when(asyncContext.getResponse()).thenReturn(asyncResponse);
        FilterChain chain = mock(FilterChain.class);
        doAnswer(invocation -> {
            ((HttpServletResponse) invocation.getArgument(1)).setStatus(200);
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        ArgumentCaptor<AsyncListener> listenerCaptor = ArgumentCaptor.forClass(AsyncListener.class);
        verify(asyncContext).addListener(listenerCaptor.capture());
        listenerCaptor.getValue().onComplete(new AsyncEvent(asyncContext));

        AssertUtils.assertLogSize(listAppender, 1);
        String message = listAppender.list.get(0).getFormattedMessage();
        assertTrue(message.contains("Status=[202]"));
    }

    @Test
    void reRegistersOnlyEachDistinctAsyncContext() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/redispatch");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("User-Agent")).thenReturn("JUnit");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        when(request.isAsyncStarted()).thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(204);
        AsyncContext firstContext = mock(AsyncContext.class);
        AsyncContext secondContext = mock(AsyncContext.class);
        when(request.getAsyncContext()).thenReturn(firstContext);

        filter.doFilter(request, response, (servletRequest, servletResponse) -> { });

        ArgumentCaptor<AsyncListener> firstCaptor = ArgumentCaptor.forClass(AsyncListener.class);
        verify(firstContext).addListener(firstCaptor.capture());
        AsyncListener listener = firstCaptor.getValue();
        listener.onStartAsync(new AsyncEvent(secondContext));
        listener.onStartAsync(new AsyncEvent(firstContext));
        listener.onStartAsync(new AsyncEvent(secondContext));

        verify(firstContext, times(1)).addListener(any(AsyncListener.class));
        verify(secondContext, times(1)).addListener(any(AsyncListener.class));
        assertEquals(0, listAppender.list.size());
        ArgumentCaptor<AsyncListener> secondCaptor = ArgumentCaptor.forClass(AsyncListener.class);
        verify(secondContext).addListener(secondCaptor.capture());
        listener.onComplete(new AsyncEvent(secondContext));
        assertEquals(0, listAppender.list.size());
        secondCaptor.getValue().onComplete(new AsyncEvent(secondContext));

        AssertUtils.assertLogSize(listAppender, 1);
        assertTrue(listAppender.list.get(0).getFormattedMessage().contains("Status=[204]"));
    }

    @Test
    void logsAsyncErrorWithThrowableExactlyOnce() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/async-error-with-throwable");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("User-Agent")).thenReturn("JUnit");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        when(request.isAsyncStarted()).thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(500);
        AsyncContext asyncContext = mock(AsyncContext.class);
        when(request.getAsyncContext()).thenReturn(asyncContext);

        filter.doFilter(request, response, (servletRequest, servletResponse) -> { });

        ArgumentCaptor<AsyncListener> listenerCaptor = ArgumentCaptor.forClass(AsyncListener.class);
        verify(asyncContext).addListener(listenerCaptor.capture());
        AsyncListener listener = listenerCaptor.getValue();
        listener.onError(new AsyncEvent(asyncContext, new IllegalArgumentException("boom")));
        listener.onComplete(new AsyncEvent(asyncContext));

        AssertUtils.assertLogSize(listAppender, 1);
        String message = listAppender.list.get(0).getFormattedMessage();
        assertTrue(message.contains("Outcome=[ERROR]"));
        assertTrue(message.contains("ErrorType=[java.lang.IllegalArgumentException]"));
    }

    @Test
    void logsAsyncErrorWithoutThrowableExactlyOnce() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/async-error");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("User-Agent")).thenReturn("JUnit");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        when(request.isAsyncStarted()).thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(500);
        AsyncContext asyncContext = mock(AsyncContext.class);
        when(request.getAsyncContext()).thenReturn(asyncContext);

        filter.doFilter(request, response, (servletRequest, servletResponse) -> { });

        ArgumentCaptor<AsyncListener> listenerCaptor = ArgumentCaptor.forClass(AsyncListener.class);
        verify(asyncContext).addListener(listenerCaptor.capture());
        AsyncListener listener = listenerCaptor.getValue();
        listener.onError(new AsyncEvent(asyncContext, null));
        listener.onComplete(new AsyncEvent(asyncContext));

        AssertUtils.assertLogSize(listAppender, 1);
        String message = listAppender.list.get(0).getFormattedMessage();
        assertTrue(message.contains("Outcome=[ERROR]"));
        assertTrue(message.contains("ErrorType=[ASYNC_ERROR_WITHOUT_THROWABLE]"));
    }

    @Test
    void logsAsyncTimeoutExactlyOnce() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/async-timeout");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("User-Agent")).thenReturn("JUnit");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        when(request.isAsyncStarted()).thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(503);
        AsyncContext asyncContext = mock(AsyncContext.class);
        when(request.getAsyncContext()).thenReturn(asyncContext);

        filter.doFilter(request, response, (servletRequest, servletResponse) -> { });

        ArgumentCaptor<AsyncListener> listenerCaptor = ArgumentCaptor.forClass(AsyncListener.class);
        verify(asyncContext).addListener(listenerCaptor.capture());
        AsyncListener listener = listenerCaptor.getValue();
        listener.onTimeout(new AsyncEvent(asyncContext));
        listener.onError(new AsyncEvent(asyncContext, new IllegalStateException("late")));

        AssertUtils.assertLogSize(listAppender, 1);
        String message = listAppender.list.get(0).getFormattedMessage();
        assertTrue(message.contains("Outcome=[TIMEOUT]"));
        assertTrue(message.contains("ErrorType=[ASYNC_TIMEOUT]"));
    }

    @Test
    void logsInitialRegistrationFailureWithoutPropagating() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/async-registration");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("User-Agent")).thenReturn("JUnit");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        when(request.isAsyncStarted()).thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AsyncContext asyncContext = mock(AsyncContext.class);
        when(request.getAsyncContext()).thenReturn(asyncContext);
        doThrow(new IllegalStateException("completed"))
                .when(asyncContext).addListener(any(AsyncListener.class));

        filter.doFilter(request, response, (servletRequest, servletResponse) -> { });

        AssertUtils.assertLogSize(listAppender, 1);
        String message = listAppender.list.get(0).getFormattedMessage();
        assertTrue(message.contains("Outcome=[REGISTRATION_ERROR]"));
        assertTrue(message.contains("ErrorType=[java.lang.IllegalStateException]"));
    }

    @Test
    void logsRedispatchRegistrationFailureExactlyOnce() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/async-redispatch-failure");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("User-Agent")).thenReturn("JUnit");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        when(request.isAsyncStarted()).thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AsyncContext firstContext = mock(AsyncContext.class);
        AsyncContext secondContext = mock(AsyncContext.class);
        when(request.getAsyncContext()).thenReturn(firstContext);
        doThrow(new IllegalStateException("completed"))
                .when(secondContext).addListener(any(AsyncListener.class));

        filter.doFilter(request, response, (servletRequest, servletResponse) -> { });

        ArgumentCaptor<AsyncListener> listenerCaptor = ArgumentCaptor.forClass(AsyncListener.class);
        verify(firstContext).addListener(listenerCaptor.capture());
        AsyncListener listener = listenerCaptor.getValue();
        listener.onStartAsync(new AsyncEvent(secondContext));
        listener.onComplete(new AsyncEvent(firstContext));

        AssertUtils.assertLogSize(listAppender, 1);
        String message = listAppender.list.get(0).getFormattedMessage();
        assertTrue(message.contains("Outcome=[REGISTRATION_ERROR]"));
        assertTrue(message.contains("ErrorType=[java.lang.IllegalStateException]"));
    }

    @Test
    void usesRestartedAsyncEventResponseWhenRegistrationFails() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/async-redispatch-failure-status");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("User-Agent")).thenReturn("JUnit");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        when(request.isAsyncStarted()).thenReturn(true);

        MockHttpServletResponse initialResponse = new MockHttpServletResponse();
        initialResponse.setStatus(200);
        AsyncContext initialContext = mock(AsyncContext.class);
        AsyncContext restartedContext = mock(AsyncContext.class);
        HttpServletResponse restartedResponse = mock(HttpServletResponse.class);
        when(request.getAsyncContext()).thenReturn(initialContext);
        when(restartedContext.getResponse()).thenReturn(restartedResponse);
        when(restartedResponse.getStatus()).thenReturn(202);
        doThrow(new IllegalStateException("completed"))
                .when(restartedContext).addListener(any(AsyncListener.class));

        filter.doFilter(request, initialResponse, (servletRequest, servletResponse) -> { });

        ArgumentCaptor<AsyncListener> listenerCaptor = ArgumentCaptor.forClass(AsyncListener.class);
        verify(initialContext).addListener(listenerCaptor.capture());
        listenerCaptor.getValue().onStartAsync(new AsyncEvent(restartedContext));

        AssertUtils.assertLogSize(listAppender, 1);
        String message = listAppender.list.get(0).getFormattedMessage();
        assertTrue(message.contains("Status=[202]"),
                "重启异步周期注册失败应使用当前 AsyncContext response 的状态码: " + message);
        assertTrue(message.contains("Outcome=[REGISTRATION_ERROR]"));
    }

    @Test
    void logsAlreadyCompletedAsyncContextWithoutPropagating() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/async-completed");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("User-Agent")).thenReturn("JUnit");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        when(request.isAsyncStarted()).thenReturn(true);
        when(request.getAsyncContext()).thenThrow(new IllegalStateException("completed"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> { });

        AssertUtils.assertLogSize(listAppender, 1);
        String message = listAppender.list.get(0).getFormattedMessage();
        assertTrue(message.contains("Outcome=[REGISTRATION_ERROR]"));
        assertTrue(message.contains("ErrorType=[ASYNC_ALREADY_COMPLETED]"));
    }

    @Test
    void serializesConcurrentRedispatchAndTerminalCallbacks() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/async-concurrent");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("User-Agent")).thenReturn("JUnit");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        when(request.isAsyncStarted()).thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AsyncContext firstContext = mock(AsyncContext.class);
        AsyncContext secondContext = mock(AsyncContext.class);
        when(request.getAsyncContext()).thenReturn(firstContext);

        filter.doFilter(request, response, (servletRequest, servletResponse) -> { });

        ArgumentCaptor<AsyncListener> listenerCaptor = ArgumentCaptor.forClass(AsyncListener.class);
        verify(firstContext).addListener(listenerCaptor.capture());
        AsyncListener listener = listenerCaptor.getValue();
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            CountDownLatch registrationReady = new CountDownLatch(3);
            CountDownLatch registrationStart = new CountDownLatch(1);
            Future<?>[] registrations = new Future<?>[3];
            for (int index = 0; index < registrations.length; index++) {
                registrations[index] = executor.submit(() -> {
                    registrationReady.countDown();
                    awaitLatch(registrationStart);
                    try {
                        listener.onStartAsync(new AsyncEvent(secondContext));
                    } catch (IOException e) {
                        throw new AssertionError(e);
                    }
                });
            }
            assertTrue(registrationReady.await(5, TimeUnit.SECONDS));
            registrationStart.countDown();
            for (Future<?> registration : registrations) {
                registration.get();
            }
            ArgumentCaptor<AsyncListener> secondCaptor = ArgumentCaptor.forClass(AsyncListener.class);
            verify(secondContext).addListener(secondCaptor.capture());
            AsyncListener currentListener = secondCaptor.getValue();

            verify(secondContext, times(1)).addListener(any(AsyncListener.class));

            CountDownLatch terminalReady = new CountDownLatch(3);
            CountDownLatch terminalStart = new CountDownLatch(1);
            Future<?> complete = executor.submit(() -> {
                terminalReady.countDown();
                awaitLatch(terminalStart);
                try {
                    currentListener.onComplete(new AsyncEvent(secondContext));
                } catch (IOException e) {
                    throw new AssertionError(e);
                }
            });
            Future<?> timeout = executor.submit(() -> {
                terminalReady.countDown();
                awaitLatch(terminalStart);
                try {
                    currentListener.onTimeout(new AsyncEvent(secondContext));
                } catch (IOException e) {
                    throw new AssertionError(e);
                }
            });
            Future<?> error = executor.submit(() -> {
                terminalReady.countDown();
                awaitLatch(terminalStart);
                try {
                    currentListener.onError(new AsyncEvent(secondContext, new IllegalStateException("boom")));
                } catch (IOException e) {
                    throw new AssertionError(e);
                }
            });
            assertTrue(terminalReady.await(5, TimeUnit.SECONDS));
            terminalStart.countDown();
            complete.get();
            timeout.get();
            error.get();

            AssertUtils.assertLogSize(listAppender, 1);
        } finally {
            executor.shutdownNow();
        }
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    /**
     * 测试客户端错误（4xx）
     */
    @Test
    void testClientErrorRequest() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(request.getRequestURI()).thenReturn("/api/user/999");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");

        doAnswer(invocation -> {
            HttpServletResponse resp = invocation.getArgument(1);
            resp.setStatus(404);
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        assertEquals(1, listAppender.list.size());
        ILoggingEvent event = listAppender.list.get(0);
        String message = event.getFormattedMessage();
        assertTrue(message.contains("Status=[404]"),
                "日志消息应该包含 Status=[404]，但实际是: " + message);
        assertEquals(Level.WARN, event.getLevel(),
                "4xx 状态码应该记录为 WARN 级别，但实际是: " + event.getLevel() + ", 消息: " + message);
        assertTrue(message.contains("Outcome=[COMPLETED]"));
        assertTrue(message.contains("ErrorType=[-]"));
    }

    /**
     * 测试服务器错误（5xx）
     */
    @Test
    void testServerErrorRequest() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(request.getRequestURI()).thenReturn("/api/process");
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");

        doAnswer(invocation -> {
            HttpServletResponse resp = invocation.getArgument(1);
            resp.setStatus(500);
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        assertEquals(1, listAppender.list.size());
        ILoggingEvent event = listAppender.list.get(0);
        assertEquals(Level.ERROR, event.getLevel());
        assertTrue(event.getFormattedMessage().contains("Status=[500]"));
        assertTrue(event.getFormattedMessage().contains("Outcome=[COMPLETED]"));
        assertTrue(event.getFormattedMessage().contains("ErrorType=[-]"));
    }

    /**
     * 测试慢接口（超过阈值）
     */
    @Test
    void testSlowRequest() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(request.getRequestURI()).thenReturn("/api/export");
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("User-Agent")).thenReturn("Apache-HttpClient/4.5");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");

        doAnswer(invocation -> {
            HttpServletResponse resp = invocation.getArgument(1);
            resp.setStatus(200);
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        assertEquals(1, listAppender.list.size());
        ILoggingEvent event = listAppender.list.get(0);
        assertTrue(event.getLevel() == Level.INFO || event.getLevel() == Level.WARN);
        assertTrue(event.getFormattedMessage().contains("Status=[200]"));
    }

    /**
     * 测试带查询参数的请求
     */
    @Test
    void testRequestWithQueryStringDoesNotLogQueryParameters() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/search");
        request.setQueryString("token=secret-token&page=1");
        request.addHeader("User-Agent", "Mozilla/5.0");
        request.setRemoteAddr("192.168.1.100");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        doAnswer(invocation -> {
            HttpServletResponse resp = invocation.getArgument(1);
            resp.setStatus(200);
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        assertEquals(1, listAppender.list.size());
        ILoggingEvent event = listAppender.list.get(0);
        assertTrue(event.getFormattedMessage().contains("/api/search"));
        assertTrue(!event.getFormattedMessage().contains("token=secret-token"));
    }

    @Test
    void shouldStreamResponseWithoutBuffering() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/events");
        request.addHeader("User-Agent", "EventSource");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> {
            HttpServletResponse chainResponse = (HttpServletResponse) servletResponse;
            assertSame(response, chainResponse);
            chainResponse.getWriter().write("data: ready\n\n");
            chainResponse.flushBuffer();

            assertEquals("data: ready\n\n", response.getContentAsString());
        };

        filter.doFilter(request, response, chain);

        assertEquals("data: ready\n\n", response.getContentAsString());
    }

    @Test
    void shouldWriteDownloadResponseWithoutBuffering() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/export");
        MockHttpServletResponse response = new MockHttpServletResponse();
        byte[] content = "export-content".getBytes();
        FilterChain chain = (servletRequest, servletResponse) -> {
            HttpServletResponse chainResponse = (HttpServletResponse) servletResponse;
            assertSame(response, chainResponse);
            chainResponse.setHeader("Content-Disposition", "attachment; filename=report.csv");
            chainResponse.getOutputStream().write(content);
            chainResponse.flushBuffer();

            assertArrayEquals(content, response.getContentAsByteArray());
        };

        filter.doFilter(request, response, chain);

        assertEquals("attachment; filename=report.csv", response.getHeader("Content-Disposition"));
        assertArrayEquals(content, response.getContentAsByteArray());
    }

    @Test
    void shouldWriteLargeResponseWithoutBuffering() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/batch-export");
        MockHttpServletResponse response = new MockHttpServletResponse();
        byte[] content = new byte[1024 * 1024];
        content[0] = 1;
        content[content.length - 1] = 2;
        FilterChain chain = (servletRequest, servletResponse) -> {
            HttpServletResponse chainResponse = (HttpServletResponse) servletResponse;
            assertSame(response, chainResponse);
            chainResponse.getOutputStream().write(content);
            chainResponse.flushBuffer();

            assertArrayEquals(content, response.getContentAsByteArray());
        };

        filter.doFilter(request, response, chain);

        assertArrayEquals(content, response.getContentAsByteArray());
    }

    /**
     * 测试默认仅信任直连地址，不信任伪造的 X-Forwarded-For。
     */
    @Test
    void testUsesDirectRemoteAddressInsteadOfForwardedFor() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        request.setRequestURI("/api/test");
        request.setMethod("GET");
        request.addHeader("User-Agent", "Mozilla/5.0");
        request.addHeader("X-Forwarded-For", "203.0.113.1");
        request.setRemoteAddr("198.51.100.10");

        doAnswer(invocation -> {
            HttpServletResponse resp = invocation.getArgument(1);
            resp.setStatus(200);
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        assertEquals(1, listAppender.list.size());
        ILoggingEvent event = listAppender.list.get(0);
        assertTrue(event.getFormattedMessage().contains("IP=[198.51.100.10]"));
    }

    /**
     * 测试默认仅信任直连地址，不信任伪造的 X-Real-IP。
     */
    @Test
    void testUsesDirectRemoteAddressInsteadOfRealIp() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        request.setRequestURI("/api/test");
        request.setMethod("GET");
        request.addHeader("User-Agent", "Mozilla/5.0");
        request.addHeader("X-Real-IP", "203.0.113.2");
        request.setRemoteAddr("198.51.100.11");

        doAnswer(invocation -> {
            HttpServletResponse resp = invocation.getArgument(1);
            resp.setStatus(200);
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        assertEquals(1, listAppender.list.size());
        ILoggingEvent event = listAppender.list.get(0);
        assertTrue(event.getFormattedMessage().contains("IP=[198.51.100.11]"));
    }

    /**
     * 测试重定向请求（3xx）
     */
    @Test
    void testRedirectRequest() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(request.getRequestURI()).thenReturn("/api/redirect");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");

        doAnswer(invocation -> {
            HttpServletResponse resp = invocation.getArgument(1);
            resp.setStatus(302);
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        assertEquals(1, listAppender.list.size());
        ILoggingEvent event = listAppender.list.get(0);
        assertEquals(Level.INFO, event.getLevel());
        assertTrue(event.getFormattedMessage().contains("Status=[302]"));
    }

    /**
     * 测试多种状态码的日志级别
     */
    @Test
    void testVariousStatusCodes() throws Exception {
        int[] statusCodes = {200, 201, 204, 301, 302, 304, 400, 401, 403, 404, 500, 502, 503};
        Level[] expectedLevels = {
                Level.INFO, Level.INFO, Level.INFO,  // 2xx
                Level.INFO, Level.INFO, Level.INFO,  // 3xx
                Level.WARN, Level.WARN, Level.WARN, Level.WARN,  // 4xx
                Level.ERROR, Level.ERROR, Level.ERROR  // 5xx
        };

        for (int i = 0; i < statusCodes.length; i++) {
            int statusCode = statusCodes[i];
            Level expectedLevel = expectedLevels[i];

            HttpServletRequest request = mock(HttpServletRequest.class);
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            when(request.getRequestURI()).thenReturn("/api/test");
            when(request.getMethod()).thenReturn("GET");
            when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
            when(request.getRemoteAddr()).thenReturn("192.168.1.100");

            final int finalStatusCode = statusCode;
            doAnswer(invocation -> {
                HttpServletResponse resp = invocation.getArgument(1);
                resp.setStatus(finalStatusCode);
                return null;
            }).when(chain).doFilter(any(), any());

            filter.doFilter(request, response, chain);

            assertEquals(1, listAppender.list.size());
            ILoggingEvent event = listAppender.list.get(0);
            assertEquals(expectedLevel, event.getLevel(),
                    "状态码 " + statusCode + " 应该是 " + expectedLevel);
            assertTrue(event.getFormattedMessage().contains("Status=[" + statusCode + "]"));

            listAppender.list.clear();
        }
    }

    /**
     * 测试日志注入防护
     * 验证恶意输入（包含换行符等特殊字符）不会被用来伪造日志条目
     */
    @Test
    void testLogInjectionPrevention() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(request.getRequestURI()).thenReturn("/api/test\n[伪造日志]");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0\r\n伪造的日志");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");

        doAnswer(invocation -> {
            HttpServletResponse resp = invocation.getArgument(1);
            resp.setStatus(200);
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        assertEquals(1, listAppender.list.size());
        ILoggingEvent event = listAppender.list.get(0);
        String message = event.getFormattedMessage();

        assertTrue(message.contains("\\n"), "换行符应该被转义为 \\n");
        assertEquals(1, listAppender.list.size(), "应该只有一条日志，不应该被注入额外的日志条目");
    }

    /**
     * 测试包含制表符的输入
     */
    @Test
    void testTabCharacterInInput() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(request.getRequestURI()).thenReturn("/api/test\twith\ttab");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");

        doAnswer(invocation -> {
            HttpServletResponse resp = invocation.getArgument(1);
            resp.setStatus(200);
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        assertEquals(1, listAppender.list.size());
        ILoggingEvent event = listAppender.list.get(0);
        String message = event.getFormattedMessage();

        assertTrue(message.contains("\\t"), "制表符应该被转义为 \\t");
    }

    @Test
    void readmeExamplesDocumentTerminalOutcomeFields() throws Exception {
        String readme = Files.readString(Path.of("README.md"));
        List<String> examples = readme.lines()
                .filter(line -> line.contains("Status=["))
                .toList();

        assertEquals(4, examples.size());
        assertTrue(examples.stream().allMatch(line -> line.contains("Outcome=[")
                && line.contains("ErrorType=[")
                && line.contains("Duration=[")));
        assertTrue(readme.contains("HTTP 5xx 只决定日志级别，不等于处理链异常"));
    }
}
