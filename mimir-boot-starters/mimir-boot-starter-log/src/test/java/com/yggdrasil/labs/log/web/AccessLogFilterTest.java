package com.yggdrasil.labs.log.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.yggdrasil.labs.test.base.BaseUnitTest;
import com.yggdrasil.labs.test.util.AssertUtils;
import com.yggdrasil.labs.test.util.FilterChainMockBuilder;
import com.yggdrasil.labs.test.util.HttpServletRequestMockBuilder;
import com.yggdrasil.labs.test.util.LogTestUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        filter = new AccessLogFilter(1000, null);
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
        HttpServletRequest request = HttpServletRequestMockBuilder.create()
                .uri("/api/user/123")
                .method("GET")
                .userAgent("Mozilla/5.0")
                .remoteAddr("192.168.1.100")
                .defaultIpHeaders()
                .build();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = FilterChainMockBuilder.create()
                .statusCode(200)
                .build();

        filter.doFilter(request, response, chain);

        AssertUtils.assertLogSize(listAppender, 1);
        ILoggingEvent event = listAppender.list.get(0);
        AssertUtils.assertLogLevel(event, Level.INFO);
        AssertUtils.assertLogStatus(event, 200);
        AssertUtils.assertLogContains(event, "GET");
        AssertUtils.assertLogContains(event, "/api/user/123");
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
        when(request.getQueryString()).thenReturn(null);
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getHeader("Proxy-Client-IP")).thenReturn(null);
        when(request.getHeader("WL-Proxy-Client-IP")).thenReturn(null);

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
        when(request.getQueryString()).thenReturn(null);
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getHeader("Proxy-Client-IP")).thenReturn(null);
        when(request.getHeader("WL-Proxy-Client-IP")).thenReturn(null);

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
        when(request.getQueryString()).thenReturn(null);
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("User-Agent")).thenReturn("Apache-HttpClient/4.5");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getHeader("Proxy-Client-IP")).thenReturn(null);
        when(request.getHeader("WL-Proxy-Client-IP")).thenReturn(null);

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
    void testRequestWithQueryString() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(request.getRequestURI()).thenReturn("/api/search");
        when(request.getQueryString()).thenReturn("keyword=test&page=1");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getHeader("Proxy-Client-IP")).thenReturn(null);
        when(request.getHeader("WL-Proxy-Client-IP")).thenReturn(null);

        doAnswer(invocation -> {
            HttpServletResponse resp = invocation.getArgument(1);
            resp.setStatus(200);
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        assertEquals(1, listAppender.list.size());
        ILoggingEvent event = listAppender.list.get(0);
        assertTrue(event.getFormattedMessage().contains("keyword=test&page=1"));
    }

    /**
     * 测试获取真实 IP（X-Forwarded-For）
     */
    @Test
    void testRealIpFromXForwardedFor() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getQueryString()).thenReturn(null);
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.1");

        doAnswer(invocation -> {
            HttpServletResponse resp = invocation.getArgument(1);
            resp.setStatus(200);
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        assertEquals(1, listAppender.list.size());
        ILoggingEvent event = listAppender.list.get(0);
        assertTrue(event.getFormattedMessage().contains("IP=[203.0.113.1]"));
    }

    /**
     * 测试获取真实 IP（X-Real-IP）
     */
    @Test
    void testRealIpFromXRealIp() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getQueryString()).thenReturn(null);
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn("203.0.113.2");

        doAnswer(invocation -> {
            HttpServletResponse resp = invocation.getArgument(1);
            resp.setStatus(200);
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        assertEquals(1, listAppender.list.size());
        ILoggingEvent event = listAppender.list.get(0);
        assertTrue(event.getFormattedMessage().contains("IP=[203.0.113.2]"));
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
        when(request.getQueryString()).thenReturn(null);
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getHeader("Proxy-Client-IP")).thenReturn(null);
        when(request.getHeader("WL-Proxy-Client-IP")).thenReturn(null);

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
            when(request.getQueryString()).thenReturn(null);
            when(request.getMethod()).thenReturn("GET");
            when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
            when(request.getRemoteAddr()).thenReturn("192.168.1.100");
            when(request.getHeader("X-Forwarded-For")).thenReturn(null);
            when(request.getHeader("X-Real-IP")).thenReturn(null);
            when(request.getHeader("Proxy-Client-IP")).thenReturn(null);
            when(request.getHeader("WL-Proxy-Client-IP")).thenReturn(null);

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
        when(request.getQueryString()).thenReturn("param=value\r\n伪造的日志");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0\r\n伪造的日志");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getHeader("Proxy-Client-IP")).thenReturn(null);
        when(request.getHeader("WL-Proxy-Client-IP")).thenReturn(null);

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
        when(request.getQueryString()).thenReturn("param=value\tvalue2");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getHeader("Proxy-Client-IP")).thenReturn(null);
        when(request.getHeader("WL-Proxy-Client-IP")).thenReturn(null);

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
}

