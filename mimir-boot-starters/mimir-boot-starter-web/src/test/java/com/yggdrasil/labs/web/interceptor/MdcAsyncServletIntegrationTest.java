package com.yggdrasil.labs.web.interceptor;

import com.yggdrasil.labs.common.constant.CommonConstants;
import com.yggdrasil.labs.common.constant.HttpHeaderConstants;
import com.yggdrasil.labs.web.config.WebAutoConfiguration;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 通过嵌入式 Tomcat 验证 MDC 在真实 Servlet 异步生命周期中的恢复。
 *
 * @author Yggdrasil Labs
 * @since 2.2.1
 */
@SpringBootTest(
        classes = MdcAsyncServletIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "logging.level.root=OFF")
class MdcAsyncServletIntegrationTest {

    private static final String PATH = "/mdc/async";
    private static final String TRACE_ID = "http-trace-id";
    private static final String REQUEST_ID = "http-request-id";
    private static final String EXTERNAL_KEY = "external";
    private static final String EXTERNAL_VALUE = "must-survive";
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(5);

    @LocalServerPort
    private int port;

    @Autowired
    private AsyncEndpoint endpoint;

    @Autowired
    private MdcProbe probe;

    @Test
    void restoresMdcAcrossRealAsyncDispatchAndRedispatch() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + PATH))
                .header(HttpHeaderConstants.TRACE_ID_HEADER, TRACE_ID)
                .header(HttpHeaderConstants.REQUEST_ID_HEADER, REQUEST_ID)
                .GET()
                .build();
        CompletableFuture<HttpResponse<String>> responseFuture = HttpClient.newHttpClient()
                .sendAsync(request, HttpResponse.BodyHandlers.ofString());

        MdcSnapshot initial = endpoint.awaitInitialDispatch();
        assertEquals(DispatcherType.REQUEST, initial.dispatcherType());
        assertMdcValues(initial);

        MdcSnapshot concurrent = probe.awaitConcurrentHandlingStarted();
        assertEquals(DispatcherType.REQUEST, concurrent.dispatcherType());
        assertClearedMdcValues(concurrent);

        endpoint.complete("async-complete");

        MdcSnapshot asyncPreHandle = probe.awaitAsyncPreHandle();
        assertEquals(DispatcherType.ASYNC, asyncPreHandle.dispatcherType());
        assertMdcValues(asyncPreHandle);

        HttpResponse<String> response = responseFuture.get(AWAIT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        assertEquals(200, response.statusCode());
        assertEquals("async-complete", response.body());
        assertEquals(TRACE_ID, response.headers().firstValue(HttpHeaderConstants.TRACE_ID_HEADER).orElse(null));

        MdcSnapshot completion = probe.awaitCompletion();
        assertEquals(DispatcherType.ASYNC, completion.dispatcherType());
        assertClearedMdcValues(completion);
        assertEquals(List.of(DispatcherType.REQUEST, DispatcherType.ASYNC), probe.dispatcherTypes());
    }

    private void assertMdcValues(MdcSnapshot snapshot) {
        assertEquals(TRACE_ID, snapshot.traceId());
        assertEquals(REQUEST_ID, snapshot.requestId());
        assertEquals("127.0.0.1", snapshot.ip());
        assertEquals(EXTERNAL_VALUE, snapshot.external());
    }

    private void assertClearedMdcValues(MdcSnapshot snapshot) {
        assertNull(snapshot.traceId());
        assertNull(snapshot.requestId());
        assertNull(snapshot.ip());
        assertEquals(EXTERNAL_VALUE, snapshot.external());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({WebAutoConfiguration.class, AsyncEndpoint.class, MdcTestConfiguration.class})
    static class TestApplication {
    }

    @RestController
    static class AsyncEndpoint {

        private final CountDownLatch initialDispatch = new CountDownLatch(1);
        private final AtomicReference<MdcSnapshot> initialSnapshot = new AtomicReference<>();
        private final AtomicReference<DeferredResult<String>> pendingResult = new AtomicReference<>();

        @GetMapping(PATH)
        DeferredResult<String> handle(HttpServletRequest request) {
            initialSnapshot.set(MdcSnapshot.current(request.getDispatcherType()));
            DeferredResult<String> result = new DeferredResult<>(AWAIT_TIMEOUT.toMillis());
            pendingResult.set(result);
            initialDispatch.countDown();
            return result;
        }

        MdcSnapshot awaitInitialDispatch() throws InterruptedException {
            assertTrue(initialDispatch.await(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
            MdcSnapshot snapshot = initialSnapshot.get();
            assertNotNull(snapshot);
            return snapshot;
        }

        void complete(String value) {
            DeferredResult<String> result = pendingResult.get();
            if (result != null) {
                result.setResult(value);
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MdcTestConfiguration {

        @Bean
        MdcProbe mdcProbe() {
            return new MdcProbe();
        }

        @Bean
        MdcBeforeInterceptor mdcBeforeInterceptor(MdcProbe probe) {
            return new MdcBeforeInterceptor(probe);
        }

        @Bean
        MdcAfterInterceptor mdcAfterInterceptor(MdcProbe probe) {
            return new MdcAfterInterceptor(probe);
        }

        @Bean
        WebMvcConfigurer mdcProbeMvcConfigurer(
                MdcBeforeInterceptor beforeInterceptor, MdcAfterInterceptor afterInterceptor) {
            return new WebMvcConfigurer() {
                @Override
                public void addInterceptors(InterceptorRegistry registry) {
                    registry.addInterceptor(beforeInterceptor).addPathPatterns(PATH).order(-1000);
                    registry.addInterceptor(afterInterceptor).addPathPatterns(PATH).order(1000);
                }
            };
        }
    }

    static class MdcBeforeInterceptor implements AsyncHandlerInterceptor {

        private final MdcProbe probe;

        MdcBeforeInterceptor(MdcProbe probe) {
            this.probe = probe;
        }

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            org.slf4j.MDC.put(EXTERNAL_KEY, EXTERNAL_VALUE);
            return true;
        }

        @Override
        public void afterConcurrentHandlingStarted(
                HttpServletRequest request, HttpServletResponse response, Object handler) {
            probe.recordConcurrentHandlingStarted(MdcSnapshot.current(request.getDispatcherType()));
            org.slf4j.MDC.remove(EXTERNAL_KEY);
        }

        @Override
        public void afterCompletion(
                HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
            probe.recordCompletion(MdcSnapshot.current(request.getDispatcherType()));
            org.slf4j.MDC.remove(EXTERNAL_KEY);
        }
    }

    static class MdcAfterInterceptor implements AsyncHandlerInterceptor {

        private final MdcProbe probe;

        MdcAfterInterceptor(MdcProbe probe) {
            this.probe = probe;
        }

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            probe.recordPreHandle(MdcSnapshot.current(request.getDispatcherType()));
            return true;
        }
    }

    static class MdcProbe {

        private final List<MdcSnapshot> preHandles = new CopyOnWriteArrayList<>();
        private final AtomicReference<MdcSnapshot> concurrentHandlingStarted = new AtomicReference<>();
        private final AtomicReference<MdcSnapshot> completion = new AtomicReference<>();
        private final CountDownLatch concurrentHandlingStartedLatch = new CountDownLatch(1);
        private final CountDownLatch completionLatch = new CountDownLatch(1);

        void recordPreHandle(MdcSnapshot snapshot) {
            preHandles.add(snapshot);
        }

        void recordConcurrentHandlingStarted(MdcSnapshot snapshot) {
            concurrentHandlingStarted.set(snapshot);
            concurrentHandlingStartedLatch.countDown();
        }

        void recordCompletion(MdcSnapshot snapshot) {
            completion.set(snapshot);
            completionLatch.countDown();
        }

        MdcSnapshot awaitConcurrentHandlingStarted() throws InterruptedException {
            assertTrue(concurrentHandlingStartedLatch.await(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
            return require(concurrentHandlingStarted.get());
        }

        MdcSnapshot awaitAsyncPreHandle() throws InterruptedException {
            long deadline = System.nanoTime() + AWAIT_TIMEOUT.toNanos();
            while (preHandles.size() < 2 && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertEquals(2, preHandles.size());
            return preHandles.get(1);
        }

        MdcSnapshot awaitCompletion() throws InterruptedException {
            assertTrue(completionLatch.await(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
            return require(completion.get());
        }

        List<DispatcherType> dispatcherTypes() {
            return preHandles.stream().map(MdcSnapshot::dispatcherType).toList();
        }

        private MdcSnapshot require(MdcSnapshot snapshot) {
            assertNotNull(snapshot);
            return snapshot;
        }
    }

    record MdcSnapshot(DispatcherType dispatcherType, String traceId, String requestId, String ip, String external) {

        static MdcSnapshot current(DispatcherType dispatcherType) {
            return new MdcSnapshot(
                    dispatcherType,
                    org.slf4j.MDC.get(CommonConstants.TRACE_ID),
                    org.slf4j.MDC.get(CommonConstants.REQUEST_ID),
                    org.slf4j.MDC.get("ip"),
                    org.slf4j.MDC.get(EXTERNAL_KEY));
        }
    }
}
