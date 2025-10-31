package com.yggdrasil.labs.web.interceptor;

import com.yggdrasil.labs.common.constant.CommonConstants;
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
 * Trace 拦截器测试
 *
 * <p>测试 TraceInterceptor 的功能：</p>
 * <ul>
 * <li>从请求头获取 traceId</li>
 * <li>生成新的 traceId</li>
 * <li>设置到 MDC 和响应头</li>
 * </ul>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class TraceInterceptorTest {

    private TraceInterceptor traceInterceptor;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private Object handler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        traceInterceptor = new TraceInterceptor();
        // 清理 MDC
        org.slf4j.MDC.clear();
    }

    @AfterEach
    void tearDown() {
        // 清理 MDC
        org.slf4j.MDC.clear();
    }

    /**
     * 测试从请求头获取 traceId
     */
    @Test
    void testPreHandleWithTraceIdFromHeader() {
        // 设置请求头中的 traceId
        String expectedTraceId = "test-trace-id-12345";
        when(request.getHeader(CommonConstants.TRACE_ID_HEADER)).thenReturn(expectedTraceId);

        // 执行拦截器
        boolean result = traceInterceptor.preHandle(request, response, handler);

        // 验证返回 true
        assertTrue(result);

        // 验证 traceId 已设置到 MDC
        assertEquals(expectedTraceId, org.slf4j.MDC.get("traceId"));

        // 验证 traceId 已设置到响应头
        verify(response).setHeader(CommonConstants.TRACE_ID_HEADER, expectedTraceId);
    }

    /**
     * 测试生成新的 traceId（当请求头中没有时）
     */
    @Test
    void testPreHandleWithGeneratedTraceId() {
        // 请求头中没有 traceId
        when(request.getHeader(CommonConstants.TRACE_ID_HEADER)).thenReturn(null);

        // 执行拦截器
        boolean result = traceInterceptor.preHandle(request, response, handler);

        // 验证返回 true
        assertTrue(result);

        // 验证已生成并设置 traceId 到 MDC
        String traceId = org.slf4j.MDC.get("traceId");
        assertNotNull(traceId);
        assertFalse(traceId.isEmpty());
        // UUID 去除连字符后应该是 32 位
        assertEquals(32, traceId.length());

        // 验证 traceId 已设置到响应头
        verify(response).setHeader(eq(CommonConstants.TRACE_ID_HEADER), anyString());
    }

    /**
     * 测试从 MDC 获取已存在的 traceId
     */
    @Test
    void testPreHandleWithExistingTraceIdInMdc() {
        // 先在 MDC 中设置 traceId
        String existingTraceId = "existing-trace-id";
        org.slf4j.MDC.put("traceId", existingTraceId);

        // 请求头中没有 traceId
        when(request.getHeader(CommonConstants.TRACE_ID_HEADER)).thenReturn(null);

        // 执行拦截器
        boolean result = traceInterceptor.preHandle(request, response, handler);

        // 验证返回 true
        assertTrue(result);

        // 验证使用了 MDC 中已存在的 traceId
        assertEquals(existingTraceId, org.slf4j.MDC.get("traceId"));

        // 验证 traceId 已设置到响应头
        verify(response).setHeader(CommonConstants.TRACE_ID_HEADER, existingTraceId);
    }

    /**
     * 测试请求头中的 traceId 优先级高于 MDC
     */
    @Test
    void testPreHandleWithHeaderPriority() {
        // MDC 中已有 traceId
        org.slf4j.MDC.put("traceId", "mdc-trace-id");

        // 请求头中有不同的 traceId
        String headerTraceId = "header-trace-id";
        when(request.getHeader(CommonConstants.TRACE_ID_HEADER)).thenReturn(headerTraceId);

        // 执行拦截器
        boolean result = traceInterceptor.preHandle(request, response, handler);

        // 验证返回 true
        assertTrue(result);

        // 验证使用了请求头中的 traceId（优先级更高）
        assertEquals(headerTraceId, org.slf4j.MDC.get("traceId"));

        // 验证 traceId 已设置到响应头
        verify(response).setHeader(CommonConstants.TRACE_ID_HEADER, headerTraceId);
    }

    /**
     * 测试 afterCompletion 不清理 MDC
     */
    @Test
    void testAfterCompletion() {
        // 先设置 traceId
        String traceId = "test-trace-id";
        org.slf4j.MDC.put("traceId", traceId);

        // 执行 afterCompletion
        traceInterceptor.afterCompletion(request, response, handler, null);

        // 验证 MDC 中的 traceId 仍然存在（不清理）
        assertEquals(traceId, org.slf4j.MDC.get("traceId"));
    }
}

