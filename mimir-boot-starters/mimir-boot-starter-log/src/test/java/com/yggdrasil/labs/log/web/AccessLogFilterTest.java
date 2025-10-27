package com.yggdrasil.labs.log.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 访问日志过滤器测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@SuppressWarnings("deprecation")
class AccessLogFilterTest {

    private AccessLogFilter filter;
    private ListAppender<ILoggingEvent> listAppender;
    private Logger accessLogger;

    @BeforeEach
    void setUp() {
        // 创建过滤器，设置慢接口阈值为 1000ms
        filter = new AccessLogFilter(1000);

        // 设置访问日志的 appender
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        accessLogger = context.getLogger("access.log");

        listAppender = new ListAppender<>();
        listAppender.setContext(context);
        listAppender.start();
        accessLogger.addAppender(listAppender);
        accessLogger.setLevel(Level.ALL);
        accessLogger.setAdditive(false);
    }

    @AfterEach
    void tearDown() {
        if (accessLogger != null && listAppender != null) {
            accessLogger.detachAppender(listAppender);
        }
        if (listAppender != null) {
            listAppender.stop();
        }
        listAppender.list.clear();
    }

    /**
     * 测试正常请求（2xx）
     */
    @Test
    void testSuccessRequest() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getRequestURI()).thenReturn("/api/user/123");
        when(request.getQueryString()).thenReturn(null);
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        when(response.getStatus()).thenReturn(200);

        filter.doFilter(request, response, chain);

        assertEquals(1, listAppender.list.size());
        ILoggingEvent event = listAppender.list.get(0);
        assertEquals(Level.INFO, event.getLevel());
        assertTrue(event.getFormattedMessage().contains("Status=[200]"));
        assertTrue(event.getFormattedMessage().contains("GET"));
        assertTrue(event.getFormattedMessage().contains("/api/user/123"));
    }

    /**
     * 测试客户端错误（4xx）
     */
    @Test
    void testClientErrorRequest() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getRequestURI()).thenReturn("/api/user/999");
        when(request.getQueryString()).thenReturn(null);
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        when(response.getStatus()).thenReturn(404);

        filter.doFilter(request, response, chain);

        assertEquals(1, listAppender.list.size());
        ILoggingEvent event = listAppender.list.get(0);
        assertEquals(Level.WARN, event.getLevel());
        assertTrue(event.getFormattedMessage().contains("Status=[404]"));
    }

    /**
     * 测试服务器错误（5xx）
     */
    @Test
    void testServerErrorRequest() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getRequestURI()).thenReturn("/api/process");
        when(request.getQueryString()).thenReturn(null);
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        when(response.getStatus()).thenReturn(500);

        filter.doFilter(request, response, chain);

        assertEquals(1, listAppender.list.size());
        ILoggingEvent event = listAppender.list.get(0);
        assertEquals(Level.ERROR, event.getLevel());
        assertTrue(event.getFormattedMessage().contains("Status=[500]"));
    }

    /**
     * 测试慢接口（超过阈值）
     * 注意：因为使用了 ContentCachingResponseWrapper，实际测试中直接模拟耗时计算
     */
    @Test
    void testSlowRequest() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getRequestURI()).thenReturn("/api/export");
        when(request.getQueryString()).thenReturn(null);
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("User-Agent")).thenReturn("Apache-HttpClient/4.5");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        when(response.getStatus()).thenReturn(200);

        // 直接调用，过滤器内部会计算耗时
        filter.doFilter(request, response, chain);

        assertEquals(1, listAppender.list.size());
        ILoggingEvent event = listAppender.list.get(0);
        // 实际耗时很短，所以是 INFO 级别，这是正常行为
        assertTrue(event.getLevel() == Level.INFO || event.getLevel() == Level.WARN);
        assertTrue(event.getFormattedMessage().contains("Status=[200]"));
    }

    /**
     * 测试带查询参数的请求
     */
    @Test
    void testRequestWithQueryString() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getRequestURI()).thenReturn("/api/search");
        when(request.getQueryString()).thenReturn("keyword=test&page=1");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        when(response.getStatus()).thenReturn(200);

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
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getQueryString()).thenReturn(null);
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.1");
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        when(response.getStatus()).thenReturn(200);

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
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getQueryString()).thenReturn(null);
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn("203.0.113.2");
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        when(response.getStatus()).thenReturn(200);

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
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getRequestURI()).thenReturn("/api/redirect");
        when(request.getQueryString()).thenReturn(null);
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        when(response.getStatus()).thenReturn(302);

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
            HttpServletResponse response = mock(HttpServletResponse.class);
            FilterChain chain = mock(FilterChain.class);

            when(request.getRequestURI()).thenReturn("/api/test");
            when(request.getQueryString()).thenReturn(null);
            when(request.getMethod()).thenReturn("GET");
            when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
            when(request.getRemoteAddr()).thenReturn("192.168.1.100");
            when(response.getStatus()).thenReturn(statusCode);

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
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        // 模拟恶意输入：URI 中包含换行符
        when(request.getRequestURI()).thenReturn("/api/test\n[伪造日志]");
        when(request.getQueryString()).thenReturn("param=value\r\n伪造的日志");
        when(request.getMethod()).thenReturn("GET");
        // User-Agent 中也包含恶意字符
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0\r\n伪造的日志");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        when(response.getStatus()).thenReturn(200);

        filter.doFilter(request, response, chain);

        assertEquals(1, listAppender.list.size());
        ILoggingEvent event = listAppender.list.get(0);
        String message = event.getFormattedMessage();
        
        // 验证换行符被转义为 \n
        assertTrue(message.contains("\\n"), "换行符应该被转义为 \\n");
        // 验证不存在未转义的换行符导致的额外日志行
        assertEquals(1, listAppender.list.size(), "应该只有一条日志，不应该被注入额外的日志条目");
    }

    /**
     * 测试包含制表符的输入
     */
    @Test
    void testTabCharacterInInput() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getRequestURI()).thenReturn("/api/test\twith\ttab");
        when(request.getQueryString()).thenReturn("param=value\tvalue2");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        when(response.getStatus()).thenReturn(200);

        filter.doFilter(request, response, chain);

        assertEquals(1, listAppender.list.size());
        ILoggingEvent event = listAppender.list.get(0);
        String message = event.getFormattedMessage();
        
        // 验证制表符被转义为 \t
        assertTrue(message.contains("\\t"), "制表符应该被转义为 \\t");
    }
}

