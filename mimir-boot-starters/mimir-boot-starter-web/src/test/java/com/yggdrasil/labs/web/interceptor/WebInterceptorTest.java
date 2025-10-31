package com.yggdrasil.labs.web.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Web 拦截器测试
 *
 * <p>测试 WebInterceptor 的功能：</p>
 * <ul>
 * <li>提取客户端 IP</li>
 * <li>设置到 MDC</li>
 * <li>清理 MDC 上下文</li>
 * </ul>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class WebInterceptorTest {

    private WebInterceptor webInterceptor;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private Object handler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        webInterceptor = new WebInterceptor();
        // 清理 MDC
        org.slf4j.MDC.clear();
    }

    @AfterEach
    void tearDown() {
        // 清理 MDC
        org.slf4j.MDC.clear();
    }

    /**
     * 测试从 X-Forwarded-For 获取 IP
     */
    @Test
    void testPreHandleWithXForwardedFor() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("192.168.1.100");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        boolean result = webInterceptor.preHandle(request, response, handler);

        assertTrue(result);
        assertEquals("192.168.1.100", org.slf4j.MDC.get("ip"));
    }

    /**
     * 测试从 X-Forwarded-For 获取第一个 IP（多个 IP 的情况）
     */
    @Test
    void testPreHandleWithMultipleIpsInXForwardedFor() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("192.168.1.100, 10.0.0.1, 172.16.0.1");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        boolean result = webInterceptor.preHandle(request, response, handler);

        assertTrue(result);
        assertEquals("192.168.1.100", org.slf4j.MDC.get("ip"));
    }

    /**
     * 测试从 X-Real-IP 获取 IP
     */
    @Test
    void testPreHandleWithXRealIp() {
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn("192.168.1.200");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        boolean result = webInterceptor.preHandle(request, response, handler);

        assertTrue(result);
        assertEquals("192.168.1.200", org.slf4j.MDC.get("ip"));
    }

    /**
     * 测试从 Proxy-Client-IP 获取 IP
     */
    @Test
    void testPreHandleWithProxyClientIp() {
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getHeader("Proxy-Client-IP")).thenReturn("192.168.1.300");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        boolean result = webInterceptor.preHandle(request, response, handler);

        assertTrue(result);
        assertEquals("192.168.1.300", org.slf4j.MDC.get("ip"));
    }

    /**
     * 测试从 WL-Proxy-Client-IP 获取 IP
     */
    @Test
    void testPreHandleWithWLProxyClientIp() {
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getHeader("Proxy-Client-IP")).thenReturn(null);
        when(request.getHeader("WL-Proxy-Client-IP")).thenReturn("192.168.1.400");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        boolean result = webInterceptor.preHandle(request, response, handler);

        assertTrue(result);
        assertEquals("192.168.1.400", org.slf4j.MDC.get("ip"));
    }

    /**
     * 测试使用 getRemoteAddr 作为兜底
     */
    @Test
    void testPreHandleWithRemoteAddr() {
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getHeader("Proxy-Client-IP")).thenReturn(null);
        when(request.getHeader("WL-Proxy-Client-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.168.1.500");

        boolean result = webInterceptor.preHandle(request, response, handler);

        assertTrue(result);
        assertEquals("192.168.1.500", org.slf4j.MDC.get("ip"));
    }

    /**
     * 测试忽略 "unknown" 值
     */
    @Test
    void testPreHandleIgnoreUnknown() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("unknown");
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.168.1.600");

        boolean result = webInterceptor.preHandle(request, response, handler);

        assertTrue(result);
        assertEquals("192.168.1.600", org.slf4j.MDC.get("ip"));
    }

    /**
     * 测试清理 MDC 上下文
     */
    @Test
    void testAfterCompletionClearsMdc() {
        // 设置一些 MDC 值
        org.slf4j.MDC.put("traceId", "test-trace-id");
        org.slf4j.MDC.put("ip", "192.168.1.100");
        org.slf4j.MDC.put("userId", "test-user");

        // 执行 afterCompletion
        webInterceptor.afterCompletion(request, response, handler, null);

        // 验证 MDC 已被清理
        assertNull(org.slf4j.MDC.get("traceId"));
        assertNull(org.slf4j.MDC.get("ip"));
        assertNull(org.slf4j.MDC.get("userId"));
    }

    /**
     * 测试异常情况下也清理 MDC
     */
    @Test
    void testAfterCompletionWithException() {
        // 设置一些 MDC 值
        org.slf4j.MDC.put("traceId", "test-trace-id");
        org.slf4j.MDC.put("ip", "192.168.1.100");

        // 执行 afterCompletion（带异常）
        Exception ex = new RuntimeException("Test exception");
        webInterceptor.afterCompletion(request, response, handler, ex);

        // 验证 MDC 已被清理
        assertNull(org.slf4j.MDC.get("traceId"));
        assertNull(org.slf4j.MDC.get("ip"));
    }
}

