package com.yggdrasil.labs.test.util;

import jakarta.servlet.http.HttpServletRequest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yggdrasil.labs.common.constant.HttpHeaderConstants;

/**
 * HttpServletRequest Mock 构建器
 *
 * <p>提供链式 API 简化 HttpServletRequest 的 mock 设置，减少重复代码。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * HttpServletRequest request = HttpServletRequestMockBuilder.create()
 *     .uri("/api/user/123")
 *     .method("GET")
 *     .userAgent("Mozilla/5.0")
 *     .remoteAddr("192.168.1.100")
 *     .ipHeaders(null, null, null, null)
 *     .build();
 * }</pre>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
public class HttpServletRequestMockBuilder {

    private final HttpServletRequest request;
    private String uri;
    private String queryString;
    private String method;
    private String userAgent;
    private String remoteAddr;
    private String xForwardedFor;
    private String xRealIp;
    private String proxyClientIp;
    private String wlProxyClientIp;
    private boolean ipHeadersCalled = false;

    private HttpServletRequestMockBuilder(HttpServletRequest request) {
        this.request = request;
    }

    /**
     * 创建构建器
     *
     * @return 构建器实例
     */
    public static HttpServletRequestMockBuilder create() {
        return new HttpServletRequestMockBuilder(mock(HttpServletRequest.class));
    }

    /**
     * 设置请求 URI
     *
     * @param uri URI
     * @return 构建器实例
     */
    public HttpServletRequestMockBuilder uri(String uri) {
        this.uri = uri;
        return this;
    }

    /**
     * 设置查询字符串
     *
     * @param queryString 查询字符串
     * @return 构建器实例
     */
    public HttpServletRequestMockBuilder queryString(String queryString) {
        this.queryString = queryString;
        return this;
    }

    /**
     * 设置 HTTP 方法
     *
     * @param method HTTP 方法（GET, POST, PUT, DELETE 等）
     * @return 构建器实例
     */
    public HttpServletRequestMockBuilder method(String method) {
        this.method = method;
        return this;
    }

    /**
     * 设置 User-Agent
     *
     * @param userAgent User-Agent
     * @return 构建器实例
     */
    public HttpServletRequestMockBuilder userAgent(String userAgent) {
        this.userAgent = userAgent;
        return this;
    }

    /**
     * 设置远程地址
     *
     * @param remoteAddr 远程地址（IP）
     * @return 构建器实例
     */
    public HttpServletRequestMockBuilder remoteAddr(String remoteAddr) {
        this.remoteAddr = remoteAddr;
        return this;
    }

    /**
     * 设置请求头
     *
     * @param name  请求头名称
     * @param value 请求头值
     * @return 构建器实例
     */
    public HttpServletRequestMockBuilder header(String name, String value) {
        when(request.getHeader(name)).thenReturn(value);
        return this;
    }

    /**
     * 设置所有 IP 相关请求头
     *
     * @param xForwardedFor   X-Forwarded-For
     * @param xRealIp         X-Real-IP
     * @param proxyClientIp   Proxy-Client-IP
     * @param wlProxyClientIp WL-Proxy-Client-IP
     * @return 构建器实例
     */
    public HttpServletRequestMockBuilder ipHeaders(String xForwardedFor, String xRealIp,
                                                   String proxyClientIp, String wlProxyClientIp) {
        this.xForwardedFor = xForwardedFor;
        this.xRealIp = xRealIp;
        this.proxyClientIp = proxyClientIp;
        this.wlProxyClientIp = wlProxyClientIp;
        this.ipHeadersCalled = true;
        return this;
    }

    /**
     * 设置默认的 IP 请求头（全部为 null，使用 remoteAddr）
     *
     * @return 构建器实例
     */
    public HttpServletRequestMockBuilder defaultIpHeaders() {
        return ipHeaders(null, null, null, null);
    }

    /**
     * 构建 HttpServletRequest mock
     *
     * @return HttpServletRequest mock
     */
    public HttpServletRequest build() {
        if (uri != null) {
            when(request.getRequestURI()).thenReturn(uri);
        }
        if (queryString != null) {
            when(request.getQueryString()).thenReturn(queryString);
        } else {
            when(request.getQueryString()).thenReturn(null);
        }
        if (method != null) {
            when(request.getMethod()).thenReturn(method);
        }
        if (userAgent != null) {
            when(request.getHeader("User-Agent")).thenReturn(userAgent);
        }
        if (remoteAddr != null) {
            when(request.getRemoteAddr()).thenReturn(remoteAddr);
        }

        // 设置 IP 相关请求头（即使值为 null 也要设置，确保 getHeader() 调用不会失败）
        // 如果调用了 ipHeaders() 或 defaultIpHeaders() 方法，必须设置这些 header 的 mock
        if (ipHeadersCalled) {
            when(request.getHeader(HttpHeaderConstants.X_FORWARDED_FOR)).thenReturn(xForwardedFor);
            when(request.getHeader(HttpHeaderConstants.X_REAL_IP)).thenReturn(xRealIp);
            when(request.getHeader(HttpHeaderConstants.PROXY_CLIENT_IP)).thenReturn(proxyClientIp);
            when(request.getHeader(HttpHeaderConstants.WL_PROXY_CLIENT_IP)).thenReturn(wlProxyClientIp);
        }

        return request;
    }
}

