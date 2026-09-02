package com.yggdrasil.labs.log.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.yggdrasil.labs.test.util.LogTestUtils;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 访问日志异步 Servlet 集成测试。
 *
 * <p>使用嵌入式 Servlet 容器而非手工构造 {@code AsyncContext}，覆盖 Spring MVC 异步派发和
 * Servlet 再次 {@code startAsync()} 时的监听器重注册。</p>
 *
 * @author Yggdrasil Labs
 * @since 2.2.1
 */
class AccessLogFilterServletIntegrationTest {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final long AWAIT_TIMEOUT_SECONDS = 5;
    private static final long LOG_STABILITY_MILLIS = 100;

    @TempDir
    static Path logPath;
    private String previousLogPath;
    private ListAppender<ILoggingEvent> listAppender;
    private Logger accessLogger;

    @BeforeEach
    void setUp() {
        previousLogPath = System.getProperty("LOG_PATH");
        System.setProperty("LOG_PATH", logPath.toString());
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        accessLogger = context.getLogger("access.log");
    }

    @AfterEach
    void tearDown() {
        LogTestUtils.cleanupLogger(accessLogger, listAppender);
        if (previousLogPath == null) {
            System.clearProperty("LOG_PATH");
        } else {
            System.setProperty("LOG_PATH", previousLogPath);
        }
    }

    @Test
    void logsFinalResponseAfterSpringMvcAsyncDispatch() throws Exception {
        try (ConfigurableApplicationContext context = startServer()) {
            WebServerApplicationContext webContext = (WebServerApplicationContext) context;
            attachAccessAppender();
            AsyncLifecycleProbe probe = context.getBean(AsyncLifecycleProbe.class);
            DeferredResultEndpoint endpoint = context.getBean(DeferredResultEndpoint.class);

            CompletableFuture<HttpResponse<String>> response = sendAsync(
                    webContext.getWebServer().getPort(), "/access-log/deferred");
            assertTrue(endpoint.awaitRequest(), "控制器应收到异步请求");
            assertTrue(probe.awaitInitialAsyncFilterReturn(), "访问日志过滤器应在首次异步派发后完成监听器注册");

            endpoint.complete(HttpStatus.CREATED);

            HttpResponse<String> result = response.get(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertEquals(HttpStatus.CREATED.value(), result.statusCode());
            assertEquals("deferred", result.body());
            assertAccessLog("/access-log/deferred", HttpStatus.CREATED.value());
        }
    }

    @Test
    void reRegistersListenerWhenServletRestartsAsyncCycle() throws Exception {
        try (ConfigurableApplicationContext context = startServer()) {
            WebServerApplicationContext webContext = (WebServerApplicationContext) context;
            attachAccessAppender();
            AsyncLifecycleProbe probe = context.getBean(AsyncLifecycleProbe.class);

            CompletableFuture<HttpResponse<String>> response = sendAsync(
                    webContext.getWebServer().getPort(), "/access-log/restarted");

            assertTrue(probe.awaitRestartedAsyncCycle(), "应观察到重启异步周期");
            assertTrue(probe.awaitRestartedAsyncFilterReturn(),
                    "第二轮异步上下文必须在访问日志过滤器返回后仍保持挂起");
            assertEquals(0, matchingAccessLogs("/access-log/restarted").size(),
                    "第二轮异步完成前不应由同步 finally 兜底输出访问日志");
            assertTrue(probe.reusedSameAsyncContext(),
                    "本测试只有在容器实际复用同一 AsyncContext identity 时才覆盖该契约");

            assertTrue(probe.completeRestartedAsyncCycle(), "应由测试闸门完成第二轮异步上下文");
            HttpResponse<String> result = response.get(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertEquals(HttpStatus.ACCEPTED.value(), result.statusCode());
            assertEquals("restarted", result.body());
            assertAccessLog("/access-log/restarted", HttpStatus.ACCEPTED.value());
        }
    }

    private ConfigurableApplicationContext startServer() {
        return new SpringApplicationBuilder(IntegrationTestApplication.class)
                .web(WebApplicationType.SERVLET)
                .properties("server.port=0", "LOG_PATH=" + logPath)
                .run();
    }

    private CompletableFuture<HttpResponse<String>> sendAsync(int port, String path) {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        return HttpClient.newHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    private void attachAccessAppender() {
        listAppender = LogTestUtils.setupLogger("access.log");
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        accessLogger = context.getLogger("access.log");
    }

    private void assertAccessLog(String uri, int status) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(AWAIT_TIMEOUT_SECONDS);
        long firstMatchNanos = 0;
        int matchingCount = 0;
        while (System.nanoTime() < deadline) {
            List<ILoggingEvent> matching = matchingAccessLogs(uri);
            matchingCount = matching.size();
            if (matchingCount > 1) {
                break;
            }
            if (matchingCount == 1) {
                if (firstMatchNanos == 0) {
                    firstMatchNanos = System.nanoTime();
                } else if (System.nanoTime() - firstMatchNanos
                        >= TimeUnit.MILLISECONDS.toNanos(LOG_STABILITY_MILLIS)) {
                    break;
                }
            }
            Thread.sleep(10);
        }

        List<ILoggingEvent> stableMatches = matchingAccessLogs(uri);
        assertEquals(1, stableMatches.size(), "每个异步请求只能产生一条终态访问日志");
        ILoggingEvent event = stableMatches.get(0);
        assertEquals(Level.INFO, event.getLevel());
        String message = event.getFormattedMessage();
        assertTrue(message.contains("URI=[" + uri + "]"));
        assertTrue(message.contains("Status=[" + status + "]"));
        assertTrue(message.contains("Outcome=[COMPLETED]"));
        assertTrue(message.contains("ErrorType=[-]"));
    }

    private List<ILoggingEvent> matchingAccessLogs(String uri) {
        String uriToken = "URI=[" + uri + "]";
        return listAppender.list.stream()
                .filter(event -> event.getFormattedMessage().contains(uriToken))
                .toList();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class IntegrationTestApplication {

        @Bean
        AsyncLifecycleProbe asyncLifecycleProbe() {
            return new AsyncLifecycleProbe();
        }

        @Bean
        DeferredResultEndpoint deferredResultEndpoint() {
            return new DeferredResultEndpoint();
        }

        @Bean
        FilterRegistrationBean<Filter> asyncLifecycleProbeFilter(AsyncLifecycleProbe probe) {
            FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(new AsyncLifecycleProbeFilter(probe));
            registration.setName("asyncLifecycleProbeFilter");
            registration.addUrlPatterns("/*");
            registration.setAsyncSupported(true);
            registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC);
            registration.setOrder(Integer.MIN_VALUE);
            return registration;
        }

        @Bean
        ServletRegistrationBean<RestartingAsyncServlet> restartingAsyncServlet(AsyncLifecycleProbe probe) {
            ServletRegistrationBean<RestartingAsyncServlet> registration = new ServletRegistrationBean<>(
                    new RestartingAsyncServlet(probe), "/access-log/restarted");
            registration.setName("restartingAsyncServlet");
            registration.setAsyncSupported(true);
            return registration;
        }
    }

    @RestController
    static class DeferredResultEndpoint {

        private final CountDownLatch requestReceived = new CountDownLatch(1);
        private final AtomicReference<DeferredResult<ResponseEntity<String>>> pendingResult = new AtomicReference<>();

        @GetMapping("/access-log/deferred")
        DeferredResult<ResponseEntity<String>> deferred() {
            DeferredResult<ResponseEntity<String>> result = new DeferredResult<>(REQUEST_TIMEOUT.toMillis());
            pendingResult.set(result);
            requestReceived.countDown();
            return result;
        }

        private boolean awaitRequest() throws InterruptedException {
            return requestReceived.await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        private void complete(HttpStatus status) {
            DeferredResult<ResponseEntity<String>> result = pendingResult.get();
            assertNotNull(result, "应先收到 DeferredResult 请求再完成响应");
            assertTrue(result.setResult(ResponseEntity.status(status).body("deferred")), "DeferredResult 应可完成");
        }
    }

    static class AsyncLifecycleProbe {

        private final CountDownLatch initialAsyncFilterReturned = new CountDownLatch(1);
        private final CountDownLatch restartedAsyncCycleObserved = new CountDownLatch(1);
        private final CountDownLatch restartedAsyncFilterReturned = new CountDownLatch(1);
        private final AtomicReference<AsyncContext> initialContext = new AtomicReference<>();
        private final AtomicReference<AsyncContext> restartedContext = new AtomicReference<>();

        private boolean awaitInitialAsyncFilterReturn() throws InterruptedException {
            return initialAsyncFilterReturned.await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        private boolean awaitRestartedAsyncCycle() throws InterruptedException {
            return restartedAsyncCycleObserved.await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        private boolean awaitRestartedAsyncFilterReturn() throws InterruptedException {
            return restartedAsyncFilterReturned.await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        private void recordInitialContext(AsyncContext context) {
            initialContext.set(context);
        }

        private void recordRestartedContext(AsyncContext context) {
            restartedContext.set(context);
            restartedAsyncCycleObserved.countDown();
        }

        private void recordRestartedAsyncFilterReturn() {
            restartedAsyncFilterReturned.countDown();
        }

        private boolean completeRestartedAsyncCycle() {
            AsyncContext context = restartedContext.get();
            if (context == null) {
                return false;
            }
            context.complete();
            return true;
        }

        private boolean reusedSameAsyncContext() {
            return initialContext.get() != null
                    && initialContext.get() == restartedContext.get();
        }
    }

    static class AsyncLifecycleProbeFilter implements Filter {

        private final AsyncLifecycleProbe probe;

        private AsyncLifecycleProbeFilter(AsyncLifecycleProbe probe) {
            this.probe = probe;
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            try {
                chain.doFilter(request, response);
            } finally {
                if (request.getDispatcherType() == DispatcherType.REQUEST && request.isAsyncStarted()) {
                    probe.initialAsyncFilterReturned.countDown();
                }
                if (request.getDispatcherType() == DispatcherType.ASYNC && request.isAsyncStarted()) {
                    probe.recordRestartedAsyncFilterReturn();
                }
            }
        }
    }

    static class RestartingAsyncServlet extends HttpServlet {

        private final AsyncLifecycleProbe probe;

        private RestartingAsyncServlet(AsyncLifecycleProbe probe) {
            this.probe = probe;
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            if (request.getDispatcherType() == DispatcherType.REQUEST) {
                AsyncContext initialContext = request.startAsync();
                probe.recordInitialContext(initialContext);
                initialContext.setTimeout(REQUEST_TIMEOUT.toMillis());
                initialContext.start(() -> dispatchAfterInitialListenerRegistration(initialContext));
                return;
            }

            AsyncContext restartedContext = request.startAsync();
            probe.recordRestartedContext(restartedContext);
            response.setStatus(HttpServletResponse.SC_ACCEPTED);
            response.getWriter().write("restarted");
        }

        private void dispatchAfterInitialListenerRegistration(AsyncContext initialContext) {
            try {
                if (!probe.awaitInitialAsyncFilterReturn()) {
                    HttpServletResponse response = (HttpServletResponse) initialContext.getResponse();
                    try {
                        response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "listener registration timeout");
                    } catch (IOException ignored) {
                        // 连接关闭时无法再写入错误响应。
                    }
                    initialContext.complete();
                    return;
                }
                initialContext.dispatch();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                try {
                    HttpServletResponse response = (HttpServletResponse) initialContext.getResponse();
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "interrupted");
                } catch (IOException ignored) {
                    // 连接关闭时无法再写入错误响应。
                } finally {
                    initialContext.complete();
                }
            }
        }
    }
}
