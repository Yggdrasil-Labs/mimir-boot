package com.yggdrasil.labs.test.util;

import com.yggdrasil.labs.common.constant.HttpHeaderConstants;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HttpServletRequestMockBuilder 测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class HttpServletRequestMockBuilderTest {

    @Test
    void testCreate() {
        HttpServletRequestMockBuilder builder = HttpServletRequestMockBuilder.create();
        assertNotNull(builder, "构建器不应为 null");
    }

    @Test
    void testUri() {
        HttpServletRequest request = HttpServletRequestMockBuilder.create()
                .uri("/api/user/123")
                .build();

        assertEquals("/api/user/123", request.getRequestURI(), "URI 应正确设置");
    }

    @Test
    void testQueryString() {
        HttpServletRequest request = HttpServletRequestMockBuilder.create()
                .queryString("name=test&age=20")
                .build();

        assertEquals("name=test&age=20", request.getQueryString(), "查询字符串应正确设置");
    }

    @Test
    void testQueryString_Null() {
        HttpServletRequest request = HttpServletRequestMockBuilder.create()
                .queryString(null)
                .build();

        assertNull(request.getQueryString(), "null 查询字符串应正确设置");
    }

    @Test
    void testMethod() {
        HttpServletRequest request = HttpServletRequestMockBuilder.create()
                .method("POST")
                .build();

        assertEquals("POST", request.getMethod(), "HTTP 方法应正确设置");
    }

    @Test
    void testUserAgent() {
        HttpServletRequest request = HttpServletRequestMockBuilder.create()
                .userAgent("Mozilla/5.0")
                .build();

        assertEquals("Mozilla/5.0", request.getHeader("User-Agent"), "User-Agent 应正确设置");
    }

    @Test
    void testRemoteAddr() {
        HttpServletRequest request = HttpServletRequestMockBuilder.create()
                .remoteAddr("192.168.1.100")
                .build();

        assertEquals("192.168.1.100", request.getRemoteAddr(), "远程地址应正确设置");
    }

    @Test
    void testHeader() {
        HttpServletRequest request = HttpServletRequestMockBuilder.create()
                .header("Custom-Header", "custom-value")
                .build();

        assertEquals("custom-value", request.getHeader("Custom-Header"), "自定义请求头应正确设置");
    }

    @Test
    void testHeader_Multiple() {
        HttpServletRequest request = HttpServletRequestMockBuilder.create()
                .header("Header1", "value1")
                .header("Header2", "value2")
                .header("Header3", "value3")
                .build();

        assertEquals("value1", request.getHeader("Header1"));
        assertEquals("value2", request.getHeader("Header2"));
        assertEquals("value3", request.getHeader("Header3"));
    }

    @Test
    void testIpHeaders() {
        HttpServletRequest request = HttpServletRequestMockBuilder.create()
                .ipHeaders("203.0.113.1", "203.0.113.2", "203.0.113.3", "203.0.113.4")
                .build();

        assertEquals("203.0.113.1", request.getHeader(HttpHeaderConstants.X_FORWARDED_FOR));
        assertEquals("203.0.113.2", request.getHeader(HttpHeaderConstants.X_REAL_IP));
        assertEquals("203.0.113.3", request.getHeader(HttpHeaderConstants.PROXY_CLIENT_IP));
        assertEquals("203.0.113.4", request.getHeader(HttpHeaderConstants.WL_PROXY_CLIENT_IP));
    }

    @Test
    void testIpHeaders_WithNull() {
        HttpServletRequest request = HttpServletRequestMockBuilder.create()
                .ipHeaders("203.0.113.1", null, null, null)
                .build();

        assertEquals("203.0.113.1", request.getHeader(HttpHeaderConstants.X_FORWARDED_FOR));
        assertNull(request.getHeader(HttpHeaderConstants.X_REAL_IP));
        assertNull(request.getHeader(HttpHeaderConstants.PROXY_CLIENT_IP));
        assertNull(request.getHeader(HttpHeaderConstants.WL_PROXY_CLIENT_IP));
    }

    @Test
    void testDefaultIpHeaders() {
        HttpServletRequest request = HttpServletRequestMockBuilder.create()
                .defaultIpHeaders()
                .build();

        assertNull(request.getHeader(HttpHeaderConstants.X_FORWARDED_FOR));
        assertNull(request.getHeader(HttpHeaderConstants.X_REAL_IP));
        assertNull(request.getHeader(HttpHeaderConstants.PROXY_CLIENT_IP));
        assertNull(request.getHeader(HttpHeaderConstants.WL_PROXY_CLIENT_IP));
    }

    @Test
    void testDefaultIpHeaders_WithRemoteAddr() {
        HttpServletRequest request = HttpServletRequestMockBuilder.create()
                .remoteAddr("192.168.1.100")
                .defaultIpHeaders()
                .build();

        assertEquals("192.168.1.100", request.getRemoteAddr());
        assertNull(request.getHeader(HttpHeaderConstants.X_FORWARDED_FOR));
    }

    @Test
    void testBuild_AllFields() {
        HttpServletRequest request = HttpServletRequestMockBuilder.create()
                .uri("/api/user/123")
                .queryString("name=test")
                .method("GET")
                .userAgent("Mozilla/5.0")
                .remoteAddr("192.168.1.100")
                .header("Custom-Header", "custom-value")
                .defaultIpHeaders()
                .build();

        assertEquals("/api/user/123", request.getRequestURI());
        assertEquals("name=test", request.getQueryString());
        assertEquals("GET", request.getMethod());
        assertEquals("Mozilla/5.0", request.getHeader("User-Agent"));
        assertEquals("192.168.1.100", request.getRemoteAddr());
        assertEquals("custom-value", request.getHeader("Custom-Header"));
        assertNull(request.getHeader(HttpHeaderConstants.X_FORWARDED_FOR));
    }

    @Test
    void testBuild_ChainedCalls() {
        HttpServletRequest request = HttpServletRequestMockBuilder.create()
                .uri("/api/test")
                .method("POST")
                .method("PUT")
                .build();

        assertEquals("PUT", request.getMethod(), "最后一次设置的方法应生效");
    }

    @Test
    void testBuild_WithoutIpHeaders() {
        HttpServletRequest request = HttpServletRequestMockBuilder.create()
                .uri("/api/test")
                .build();

        // 如果没有调用 ipHeaders() 或 defaultIpHeaders()，这些 header 不应该被 mock
        // 由于是 mock 对象，未设置的 header 会返回 null（Mockito 的默认行为）
        assertNull(request.getHeader(HttpHeaderConstants.X_FORWARDED_FOR));
    }
}

