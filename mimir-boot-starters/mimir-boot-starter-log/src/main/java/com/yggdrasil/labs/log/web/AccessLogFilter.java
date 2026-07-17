package com.yggdrasil.labs.log.web;

import com.yggdrasil.labs.common.util.IpUtils;
import com.yggdrasil.labs.common.util.LogSanitizer;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.List;

/**
 * 访问日志过滤器
 *
 * <p>功能说明：</p>
 * <ul>
 * <li>记录每个请求的详细信息：IP、URI、耗时、状态码</li>
 * <li>根据耗时判断是否为慢接口，慢接口输出 WARN 级别日志</li>
 * <li>慢接口阈值可配置</li>
 * <li>支持自动获取真实 IP（支持反向代理场景）</li>
 * </ul>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
public class AccessLogFilter implements Filter {

    private static final Logger ACCESS_LOG = LoggerFactory.getLogger("access.log");
    private static final String SLOW_ENDPOINT_SUFFIX = " [慢接口]";
    private final long slowThresholdMs;
    private final List<String> excludePaths;
    private final PathMatcher pathMatcher;

    public AccessLogFilter(long slowThresholdMs, List<String> excludePaths) {
        this.slowThresholdMs = slowThresholdMs;
        this.excludePaths = excludePaths != null ? excludePaths : List.of();
        this.pathMatcher = new AntPathMatcher();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // 记录开始时间
        long startTime = System.currentTimeMillis();

        // 包装响应以便获取状态码
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(httpResponse);

        try {
            // 继续过滤器链
            chain.doFilter(request, wrappedResponse);
        } finally {
            // 计算耗时
            long duration = System.currentTimeMillis() - startTime;

            // 检查是否需要记录访问日志（排除配置的路径）
            if (shouldLogAccess(httpRequest)) {
                logAccess(httpRequest, wrappedResponse, duration);
            }

            // 将缓存的响应内容复制回原始响应（重要：否则响应会为空）
            wrappedResponse.copyBodyToResponse();
        }
    }

    /**
     * 判断是否应该记录访问日志
     * 如果请求路径匹配排除列表中的任何模式，则不记录
     *
     * @param request HTTP 请求
     * @return true 如果应该记录日志，false 如果应该排除
     */
    private boolean shouldLogAccess(HttpServletRequest request) {
        if (excludePaths.isEmpty()) {
            return true;
        }

        String requestPath = request.getRequestURI();
        String contextPath = request.getContextPath();
        
        // 移除 context path（如果有）
        if (contextPath != null && !contextPath.isEmpty() && requestPath.startsWith(contextPath)) {
            requestPath = requestPath.substring(contextPath.length());
        }

        // 检查是否匹配任何排除模式
        for (String pattern : excludePaths) {
            if (pathMatcher.match(pattern, requestPath)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 记录访问日志
     */
    private void logAccess(HttpServletRequest request, HttpServletResponse response, long durationMs) {
        try {
            String ip = sanitize(getClientIp(request));
            String method = sanitize(request.getMethod());
            String uri = sanitize(request.getRequestURI());
            int statusCode = response.getStatus();
            String userAgent = sanitize(request.getHeader("User-Agent"));

            // 查询参数可能包含令牌、口令等敏感信息，只记录请求路径。
            logAccessByStatus(ip, method, uri, statusCode, durationMs, userAgent != null ? userAgent : "Unknown");
        } catch (Exception e) {
            ACCESS_LOG.error("Failed to log access", e);
        }
    }

    /**
     * 根据 HTTP 状态码和耗时决定日志级别
     * <p>
     * 最佳实践：
     * - 2xx (成功): INFO，如果慢则 WARN
     * - 3xx (重定向): INFO，如果慢则 WARN
     * - 4xx (客户端错误): WARN，如果慢则 WARN
     * - 5xx (服务器错误): ERROR，如果慢则 ERROR
     *
     * @param ip         客户端 IP
     * @param method     HTTP 方法
     * @param uri        请求路径
     * @param statusCode HTTP 状态码
     * @param durationMs 耗时（毫秒）
     * @param userAgent  User-Agent
     */
    private void logAccessByStatus(String ip, String method, String uri, int statusCode, long durationMs, String userAgent) {
        boolean isSlow = durationMs > slowThresholdMs;

        // 使用参数化日志，防止日志注入攻击
        String message = "IP=[{}], Method=[{}], URI=[{}], Status=[{}], Duration=[{}ms], UserAgent=[{}]";
        Object[] args = new Object[]{ip, method, uri, statusCode, durationMs, userAgent};

        // 判断状态码范围
        if (statusCode >= 500) {
            // 5xx: 服务器错误，记录为 ERROR
            // 示例：500 Internal Server Error, 502 Bad Gateway, 503 Service Unavailable
            ACCESS_LOG.error(message, args);
        } else if (statusCode >= 400) {
            // 4xx: 客户端错误，记录为 WARN
            // 示例：400 Bad Request, 401 Unauthorized, 403 Forbidden, 404 Not Found, 429 Too Many Requests
            if (isSlow) {
                ACCESS_LOG.warn(message + SLOW_ENDPOINT_SUFFIX, args);
            } else {
                ACCESS_LOG.warn(message, args);
            }
        } else if (statusCode >= 300) {
            // 3xx: 重定向，记录为 INFO
            // 示例：301 Moved Permanently, 302 Found, 304 Not Modified
            if (isSlow) {
                ACCESS_LOG.warn(message + SLOW_ENDPOINT_SUFFIX, args);
            } else {
                ACCESS_LOG.info(message, args);
            }
        } else {
            // 2xx: 成功，记录为 INFO
            // 示例：200 OK, 201 Created, 204 No Content
            if (isSlow) {
                ACCESS_LOG.warn(message + SLOW_ENDPOINT_SUFFIX, args);
            } else {
                ACCESS_LOG.info(message, args);
            }
        }
    }

    /**
     * 获取客户端真实 IP
     * 支持反向代理场景，按优先级检查以下请求头：
     * 1. X-Forwarded-For
     * 2. X-Real-IP
     * 3. getRemoteAddr()
     *
     * @param request HTTP 请求
     * @return 客户端真实 IP
     */
    private String getClientIp(HttpServletRequest request) {
        return IpUtils.resolveClientIp(request::getHeader, request::getRemoteAddr);
    }

    /**
     * 清理用户输入，防止日志注入攻击
     * <p>
     * 移除换行符、回车符、制表符等控制字符，防止恶意用户通过构造特殊字符来伪造日志条目
     *
     * @param input 原始输入
     * @return 清理后的字符串，如果输入为 null 则返回 null
     */
    private String sanitize(String input) {
        return LogSanitizer.escapeControls(input);
    }
}
