package com.yggdrasil.labs.web.interceptor;

import com.yggdrasil.labs.common.constant.HttpHeaderConstants;
import com.yggdrasil.labs.test.base.BaseUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebInterceptorTest extends BaseUnitTest {

    private WebInterceptor webInterceptor;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();
        webInterceptor = new WebInterceptor();
    }

    @Override
    @AfterEach
    protected void tearDown() {
        super.tearDown();
    }

    @Test
    void preHandleUsesDirectRemoteAddressInsteadOfForgedForwardedHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.10");
        request.addHeader("X-Forwarded-For", "203.0.113.10");
        request.addHeader("X-Real-IP", "203.0.113.11");
        request.addHeader("Proxy-Client-IP", "203.0.113.12");
        request.addHeader("WL-Proxy-Client-IP", "203.0.113.13");

        assertTrue(webInterceptor.preHandle(request, new MockHttpServletResponse(), new Object()));

        assertEquals("198.51.100.10", org.slf4j.MDC.get("ip"));
    }

    @Test
    void afterCompletionRestoresOnlyItsPreviousIpAndKeepsOtherMdcKeysWhenExceptionExists() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.10");
        org.slf4j.MDC.put("ip", "old-ip");
        org.slf4j.MDC.put("traceId", "trace-owned-by-trace-interceptor");
        org.slf4j.MDC.put("external", "keep-me");

        webInterceptor.preHandle(request, new MockHttpServletResponse(), new Object());
        webInterceptor.afterCompletion(request, new MockHttpServletResponse(), new Object(), new RuntimeException("expected"));

        assertEquals("old-ip", org.slf4j.MDC.get("ip"));
        assertEquals("trace-owned-by-trace-interceptor", org.slf4j.MDC.get("traceId"));
        assertEquals("keep-me", org.slf4j.MDC.get("external"));
    }

    @Test
    void nestedPreHandleAndAfterCompletionPairingRestoresEachIpValue() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.10");
        org.slf4j.MDC.put("ip", "before-request");

        webInterceptor.preHandle(request, new MockHttpServletResponse(), new Object());
        request.setRemoteAddr("198.51.100.11");
        webInterceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        webInterceptor.afterCompletion(request, new MockHttpServletResponse(), new Object(), null);
        assertEquals("198.51.100.10", org.slf4j.MDC.get("ip"));

        webInterceptor.afterCompletion(request, new MockHttpServletResponse(), new Object(), null);
        assertEquals("before-request", org.slf4j.MDC.get("ip"));
    }

    @Test
    void afterCompletionWithoutPreHandleDoesNotEraseCurrentIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        org.slf4j.MDC.put("ip", "unrelated-ip");

        webInterceptor.afterCompletion(request, new MockHttpServletResponse(), new Object(), null);

        assertEquals("unrelated-ip", org.slf4j.MDC.get("ip"));
    }

    @Test
    void traceAndWebInterceptorsRestoreTheirOwnKeysInNormalOrder() {
        TraceInterceptor traceInterceptor = new TraceInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        Object handler = new Object();
        request.setRemoteAddr("198.51.100.10");
        request.addHeader(HttpHeaderConstants.TRACE_ID_HEADER, "request-trace-id");
        request.addHeader("X-Forwarded-For", "203.0.113.10");
        org.slf4j.MDC.put("traceId", "previous-trace-id");
        org.slf4j.MDC.put("ip", "previous-ip");
        org.slf4j.MDC.put("external", "keep-me");

        traceInterceptor.preHandle(request, response, handler);
        webInterceptor.preHandle(request, response, handler);
        assertEquals("request-trace-id", org.slf4j.MDC.get("traceId"));
        assertEquals("198.51.100.10", org.slf4j.MDC.get("ip"));

        webInterceptor.afterCompletion(request, response, handler, null);
        traceInterceptor.afterCompletion(request, response, handler, null);

        assertEquals("previous-trace-id", org.slf4j.MDC.get("traceId"));
        assertEquals("previous-ip", org.slf4j.MDC.get("ip"));
        assertEquals("keep-me", org.slf4j.MDC.get("external"));
    }

    @Test
    void firstPreHandleRemovesOnlyTheIpItIntroduced() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.10");
        org.slf4j.MDC.put("external", "keep-me");

        webInterceptor.preHandle(request, new MockHttpServletResponse(), new Object());
        webInterceptor.afterCompletion(request, new MockHttpServletResponse(), new Object(), null);

        assertNull(org.slf4j.MDC.get("ip"));
        assertEquals("keep-me", org.slf4j.MDC.get("external"));
    }
    @Test
    void releasesIpAfterConcurrentHandlingStarted() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setRemoteAddr("198.51.100.10");
        org.slf4j.MDC.put("ip", "ip-before");
        org.slf4j.MDC.put("external", "keep-me");

        webInterceptor.preHandle(request, response, new Object());
        webInterceptor.afterConcurrentHandlingStarted(request, response, new Object());

        assertEquals("ip-before", org.slf4j.MDC.get("ip"));
        assertEquals("keep-me", org.slf4j.MDC.get("external"));
    }

    @Test
    void restoresIpOnAsyncTimeout() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setRemoteAddr("198.51.100.10");
        org.slf4j.MDC.put("ip", "ip-before");
        org.slf4j.MDC.put("external", "keep-me");

        webInterceptor.preHandle(request, response, new Object());
        webInterceptor.afterConcurrentHandlingStarted(request, response, new Object());
        request.setRemoteAddr("198.51.100.11");
        webInterceptor.preHandle(request, response, new Object());
        webInterceptor.afterCompletion(request, response, new Object(), new RuntimeException("async timeout"));

        assertEquals("ip-before", org.slf4j.MDC.get("ip"));
        assertEquals("keep-me", org.slf4j.MDC.get("external"));
    }

    @Test
    void restoresIpOnAsyncError() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setRemoteAddr("198.51.100.10");
        org.slf4j.MDC.put("ip", "ip-before");
        org.slf4j.MDC.put("external", "keep-me");

        webInterceptor.preHandle(request, response, new Object());
        webInterceptor.afterConcurrentHandlingStarted(request, response, new Object());
        request.setRemoteAddr("198.51.100.11");
        webInterceptor.preHandle(request, response, new Object());
        webInterceptor.afterCompletion(request, response, new Object(), new RuntimeException("async error"));

        assertEquals("ip-before", org.slf4j.MDC.get("ip"));
        assertEquals("keep-me", org.slf4j.MDC.get("external"));
    }

}
