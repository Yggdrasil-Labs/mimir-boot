package com.yggdrasil.labs.web.interceptor;

import com.yggdrasil.labs.common.constant.HttpHeaderConstants;
import com.yggdrasil.labs.test.base.BaseUnitTest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

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
class TraceInterceptorTest extends BaseUnitTest {

    private TraceInterceptor traceInterceptor;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private Object handler;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();
        traceInterceptor = new TraceInterceptor();
    }

    @Override
    @AfterEach
    protected void tearDown() {
        super.tearDown();
    }

    /**
     * 测试从请求头获取 traceId
     */
    @Test
    void testPreHandleWithTraceIdFromHeader() {
        // 设置请求头中的 traceId
        String expectedTraceId = "test-trace-id-12345";
        when(request.getHeader(HttpHeaderConstants.TRACE_ID_HEADER)).thenReturn(expectedTraceId);

        // 执行拦截器
        boolean result = traceInterceptor.preHandle(request, response, handler);

        // 验证返回 true
        assertTrue(result);

        // 验证 traceId 已设置到 MDC
        assertEquals(expectedTraceId, org.slf4j.MDC.get("traceId"));

        // 验证 traceId 已设置到响应头
        verify(response).setHeader(HttpHeaderConstants.TRACE_ID_HEADER, expectedTraceId);
    }

    @Test
    void testPreHandleWithInvalidTraceIdFromHeader() {
        String invalidTraceId = "invalid trace\n" + "a".repeat(65);
        when(request.getHeader(HttpHeaderConstants.TRACE_ID_HEADER)).thenReturn(invalidTraceId);

        boolean result = traceInterceptor.preHandle(request, response, handler);

        assertTrue(result);
        String traceId = org.slf4j.MDC.get("traceId");
        assertNotNull(traceId);
        assertNotEquals(invalidTraceId, traceId);
        assertEquals(32, traceId.length());
        verify(response).setHeader(HttpHeaderConstants.TRACE_ID_HEADER, traceId);
    }

    @Test
    void testPreHandleWithInvalidHeaderShouldNotReuseValidTraceIdFromMdc() {
        String existingTraceId = "existing-valid-trace-id";
        org.slf4j.MDC.put("traceId", existingTraceId);
        when(request.getHeader(HttpHeaderConstants.TRACE_ID_HEADER)).thenReturn("invalid trace id");

        boolean result = traceInterceptor.preHandle(request, response, handler);

        assertTrue(result);
        String traceId = org.slf4j.MDC.get("traceId");
        assertNotNull(traceId);
        assertNotEquals(existingTraceId, traceId);
        assertEquals(32, traceId.length());
        verify(response).setHeader(HttpHeaderConstants.TRACE_ID_HEADER, traceId);
    }

    @Test
    void testPreHandleShouldReplaceInvalidTraceIdInMdc() {
        String invalidTraceId = "invalid trace\n" + "a".repeat(65);
        org.slf4j.MDC.put("traceId", invalidTraceId);
        when(request.getHeader(HttpHeaderConstants.TRACE_ID_HEADER)).thenReturn(invalidTraceId);

        boolean result = traceInterceptor.preHandle(request, response, handler);

        assertTrue(result);
        String traceId = org.slf4j.MDC.get("traceId");
        assertNotNull(traceId);
        assertNotEquals(invalidTraceId, traceId);
        assertEquals(32, traceId.length());
        verify(response).setHeader(HttpHeaderConstants.TRACE_ID_HEADER, traceId);
    }

    /**
     * 测试生成新的 traceId（当请求头中没有时）
     */
    @Test
    void testPreHandleWithGeneratedTraceId() {
        // 请求头中没有 traceId
        when(request.getHeader(HttpHeaderConstants.TRACE_ID_HEADER)).thenReturn(null);

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
        verify(response).setHeader(eq(HttpHeaderConstants.TRACE_ID_HEADER), anyString());
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
        when(request.getHeader(HttpHeaderConstants.TRACE_ID_HEADER)).thenReturn(null);

        // 执行拦截器
        boolean result = traceInterceptor.preHandle(request, response, handler);

        // 验证返回 true
        assertTrue(result);

        // 验证使用了 MDC 中已存在的 traceId
        assertEquals(existingTraceId, org.slf4j.MDC.get("traceId"));

        // 验证 traceId 已设置到响应头
        verify(response).setHeader(HttpHeaderConstants.TRACE_ID_HEADER, existingTraceId);
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
        when(request.getHeader(HttpHeaderConstants.TRACE_ID_HEADER)).thenReturn(headerTraceId);

        // 执行拦截器
        boolean result = traceInterceptor.preHandle(request, response, handler);

        // 验证返回 true
        assertTrue(result);

        // 验证使用了请求头中的 traceId（优先级更高）
        assertEquals(headerTraceId, org.slf4j.MDC.get("traceId"));

        // 验证 traceId 已设置到响应头
        verify(response).setHeader(HttpHeaderConstants.TRACE_ID_HEADER, headerTraceId);
    }

    @Test
    void afterCompletionRestoresPreviousTraceIdAndKeepsExternalMdcKeyWhenExceptionExists() {
        MockHttpServletRequest localRequest = new MockHttpServletRequest();
        localRequest.addHeader(HttpHeaderConstants.TRACE_ID_HEADER, "new-trace-id");
        org.slf4j.MDC.put("traceId", "previous-trace-id");
        org.slf4j.MDC.put("external", "keep-me");

        traceInterceptor.preHandle(localRequest, new MockHttpServletResponse(), handler);
        traceInterceptor.afterCompletion(
                localRequest, new MockHttpServletResponse(), handler, new RuntimeException("expected"));

        assertEquals("previous-trace-id", org.slf4j.MDC.get("traceId"));
        assertEquals("keep-me", org.slf4j.MDC.get("external"));
    }

    @Test
    void afterCompletionRestoresRequestIdAndKeepsExternalMdcKey() {
        MockHttpServletRequest localRequest = new MockHttpServletRequest();
        localRequest.addHeader(HttpHeaderConstants.TRACE_ID_HEADER, "new-trace-id");
        localRequest.addHeader(HttpHeaderConstants.REQUEST_ID_HEADER, "new-request-id");
        org.slf4j.MDC.put("traceId", "previous-trace-id");
        org.slf4j.MDC.put("requestId", "previous-request-id");
        org.slf4j.MDC.put("external", "keep-me");

        traceInterceptor.preHandle(localRequest, new MockHttpServletResponse(), handler);

        assertEquals("new-request-id", org.slf4j.MDC.get("requestId"));

        traceInterceptor.afterCompletion(localRequest, new MockHttpServletResponse(), handler, null);

        assertEquals("previous-trace-id", org.slf4j.MDC.get("traceId"));
        assertEquals("previous-request-id", org.slf4j.MDC.get("requestId"));
        assertEquals("keep-me", org.slf4j.MDC.get("external"));
    }

    @Test
    void missingRequestIdGeneratesNewValueAndRestoresPreviousValue() {
        MockHttpServletRequest localRequest = new MockHttpServletRequest();
        localRequest.addHeader(HttpHeaderConstants.TRACE_ID_HEADER, "new-trace-id");
        org.slf4j.MDC.put("requestId", "previous-request-id");

        traceInterceptor.preHandle(localRequest, new MockHttpServletResponse(), handler);

        assertNotEquals("previous-request-id", org.slf4j.MDC.get("requestId"));
        assertEquals(32, org.slf4j.MDC.get("requestId").length());

        traceInterceptor.afterCompletion(localRequest, new MockHttpServletResponse(), handler, null);

        assertEquals("previous-request-id", org.slf4j.MDC.get("requestId"));
    }

    @Test
    void nestedPreHandleAndAfterCompletionPairingRestoresEachTraceId() {
        MockHttpServletRequest localRequest = new MockHttpServletRequest();
        MockHttpServletResponse localResponse = new MockHttpServletResponse();
        localRequest.addHeader(HttpHeaderConstants.TRACE_ID_HEADER, "outer-trace-id");
        org.slf4j.MDC.put("traceId", "before-request");

        traceInterceptor.preHandle(localRequest, localResponse, handler);
        localRequest.removeHeader(HttpHeaderConstants.TRACE_ID_HEADER);
        localRequest.addHeader(HttpHeaderConstants.TRACE_ID_HEADER, "inner-trace-id");
        traceInterceptor.preHandle(localRequest, localResponse, handler);

        traceInterceptor.afterCompletion(localRequest, localResponse, handler, null);
        assertEquals("outer-trace-id", org.slf4j.MDC.get("traceId"));

        traceInterceptor.afterCompletion(localRequest, localResponse, handler, null);
        assertEquals("before-request", org.slf4j.MDC.get("traceId"));
    }

    @Test
    void responseHeaderFailureDoesNotLeaveNewTraceIdInMdc() {
        when(request.getHeader(HttpHeaderConstants.TRACE_ID_HEADER)).thenReturn("new-trace-id");
        org.slf4j.MDC.put("traceId", "previous-trace-id");
        doThrow(new IllegalStateException("response committed"))
                .when(response).setHeader(HttpHeaderConstants.TRACE_ID_HEADER, "new-trace-id");

        assertThrows(IllegalStateException.class, () -> traceInterceptor.preHandle(request, response, handler));

        assertEquals("previous-trace-id", org.slf4j.MDC.get("traceId"));
    }
    @Test
    void releasesMdcAfterConcurrentHandlingStarted() throws Exception {
        MockHttpServletRequest localRequest = new MockHttpServletRequest();
        MockHttpServletResponse localResponse = new MockHttpServletResponse();
        localRequest.addHeader(HttpHeaderConstants.TRACE_ID_HEADER, "async-trace-id");
        localRequest.addHeader(HttpHeaderConstants.REQUEST_ID_HEADER, "async-request-id");
        org.slf4j.MDC.put("traceId", "trace-before");
        org.slf4j.MDC.put("requestId", "request-before");
        org.slf4j.MDC.put("external", "keep-me");

        traceInterceptor.preHandle(localRequest, localResponse, handler);
        traceInterceptor.afterConcurrentHandlingStarted(localRequest, localResponse, handler);

        assertEquals("trace-before", org.slf4j.MDC.get("traceId"));
        assertEquals("request-before", org.slf4j.MDC.get("requestId"));
        assertEquals("keep-me", org.slf4j.MDC.get("external"));
    }

    @Test
    void restoresSnapshotOnAsyncError() throws Exception {
        MockHttpServletRequest localRequest = new MockHttpServletRequest();
        MockHttpServletResponse localResponse = new MockHttpServletResponse();
        localRequest.addHeader(HttpHeaderConstants.TRACE_ID_HEADER, "initial-trace-id");
        org.slf4j.MDC.put("traceId", "trace-before");
        org.slf4j.MDC.put("requestId", "request-before");
        org.slf4j.MDC.put("external", "keep-me");

        traceInterceptor.preHandle(localRequest, localResponse, handler);
        traceInterceptor.afterConcurrentHandlingStarted(localRequest, localResponse, handler);
        localRequest.removeHeader(HttpHeaderConstants.TRACE_ID_HEADER);
        localRequest.addHeader(HttpHeaderConstants.TRACE_ID_HEADER, "redispatch-trace-id");
        traceInterceptor.preHandle(localRequest, localResponse, handler);
        traceInterceptor.afterCompletion(localRequest, localResponse, handler, new RuntimeException("async error"));

        assertEquals("trace-before", org.slf4j.MDC.get("traceId"));
        assertEquals("request-before", org.slf4j.MDC.get("requestId"));
        assertEquals("keep-me", org.slf4j.MDC.get("external"));
    }

    @Test
    void restoresSnapshotOnAsyncTimeout() throws Exception {
        MockHttpServletRequest localRequest = new MockHttpServletRequest();
        MockHttpServletResponse localResponse = new MockHttpServletResponse();
        localRequest.addHeader(HttpHeaderConstants.TRACE_ID_HEADER, "initial-trace-id");
        org.slf4j.MDC.put("traceId", "trace-before");
        org.slf4j.MDC.put("requestId", "request-before");
        org.slf4j.MDC.put("external", "keep-me");

        traceInterceptor.preHandle(localRequest, localResponse, handler);
        traceInterceptor.afterConcurrentHandlingStarted(localRequest, localResponse, handler);
        localRequest.removeHeader(HttpHeaderConstants.TRACE_ID_HEADER);
        localRequest.addHeader(HttpHeaderConstants.TRACE_ID_HEADER, "redispatch-trace-id");
        traceInterceptor.preHandle(localRequest, localResponse, handler);
        traceInterceptor.afterCompletion(localRequest, localResponse, handler, new RuntimeException("async timeout"));

        assertEquals("trace-before", org.slf4j.MDC.get("traceId"));
        assertEquals("request-before", org.slf4j.MDC.get("requestId"));
        assertEquals("keep-me", org.slf4j.MDC.get("external"));
    }

    @Test
    void removesIntroducedMdcValuesAfterConcurrentHandlingStarts() throws Exception {
        MockHttpServletRequest localRequest = new MockHttpServletRequest();
        MockHttpServletResponse localResponse = new MockHttpServletResponse();
        localRequest.addHeader(HttpHeaderConstants.TRACE_ID_HEADER, "async-trace-id");
        localRequest.addHeader(HttpHeaderConstants.REQUEST_ID_HEADER, "async-request-id");
        org.slf4j.MDC.put("external", "keep-me");

        traceInterceptor.preHandle(localRequest, localResponse, handler);
        traceInterceptor.afterConcurrentHandlingStarted(localRequest, localResponse, handler);

        assertNull(org.slf4j.MDC.get("traceId"));
        assertNull(org.slf4j.MDC.get("requestId"));
        assertEquals("keep-me", org.slf4j.MDC.get("external"));
    }

}
