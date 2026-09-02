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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 访问日志过滤器
 *
 * <p>功能说明：</p>
 * <ul>
 * <li>记录每个请求的详细信息：IP、URI、耗时、状态码</li>
 * <li>根据耗时判断是否为慢接口，慢接口输出 WARN 级别日志</li>
 * <li>慢接口阈值可配置</li>
 * <li>记录容器提供的直连对端 IP</li>
 * </ul>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
public class AccessLogFilter implements Filter {

    private static final Logger ACCESS_LOG = LoggerFactory.getLogger("access.log");
    private static final Logger LOGGER = LoggerFactory.getLogger(AccessLogFilter.class);
    private static final String SLOW_ENDPOINT_SUFFIX = " [慢接口]";
    private static final String LIFECYCLE_ATTRIBUTE = AccessLogFilter.class.getName() + ".lifecycle";
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

        RequestLifecycle lifecycle = lifecycleFor(httpRequest, httpResponse);
        boolean async = false;
        Throwable failure = null;
        try {
            chain.doFilter(request, httpResponse);
            async = httpRequest.isAsyncStarted();
            if (async) {
                lifecycle.registerCurrentContext(httpRequest);
            }
        } catch (IOException | ServletException | RuntimeException e) {
            failure = e;
            throw e;
        } catch (Error e) {
            failure = e;
            throw e;
        } finally {
            if (!async) {
                lifecycle.terminate(httpResponse, -1, failure == null ? "COMPLETED" : "ERROR",
                        failure == null ? "-" : failure.getClass().getName());
            }
        }
    }

    private RequestLifecycle lifecycleFor(HttpServletRequest request, HttpServletResponse response) {
        Object existing = request.getAttribute(LIFECYCLE_ATTRIBUTE);
        if (existing instanceof RequestLifecycle lifecycle) {
            return lifecycle;
        }
        RequestLifecycle lifecycle = new RequestLifecycle(request, response);
        request.setAttribute(LIFECYCLE_ATTRIBUTE, lifecycle);
        return lifecycle;
    }

    private final class RequestLifecycle {
        private final HttpServletRequest request;
        private final HttpServletResponse response;
        private final long startNanos = System.nanoTime();
        private final AtomicReference<LifecycleState> state = new AtomicReference<>(LifecycleState.initial());

        private AsyncListener listenerFor(int registrationGeneration) {
            return new AsyncListener() {
                @Override
                public void onComplete(AsyncEvent event) {
                    safeTerminate(event, registrationGeneration, "COMPLETED", "-");
                }

                @Override
                public void onTimeout(AsyncEvent event) {
                    safeTerminate(event, registrationGeneration, "TIMEOUT", "ASYNC_TIMEOUT");
                }

                @Override
                public void onError(AsyncEvent event) {
                    Throwable failure = event.getThrowable();
                    safeTerminate(event, registrationGeneration, "ERROR", failure == null
                            ? "ASYNC_ERROR_WITHOUT_THROWABLE" : failure.getClass().getName());
                }

                @Override
                public void onStartAsync(AsyncEvent event) {
                    try {
                        registerRestartedContext(event, registrationGeneration);
                    } catch (RuntimeException e) {
                        safeTerminate(event, registrationGeneration, "REGISTRATION_ERROR", e.getClass().getName());
                    }
                }
            };
        }

        private RequestLifecycle(HttpServletRequest request, HttpServletResponse response) {
            this.request = request;
            this.response = response;
        }

        private void registerCurrentContext(HttpServletRequest request) {
            try {
                registerInitialContext(request.getAsyncContext());
            } catch (IllegalStateException e) {
                safeTerminate("REGISTRATION_ERROR", "ASYNC_ALREADY_COMPLETED");
            }
        }

        private void registerInitialContext(AsyncContext context) {
            if (context == null) {
                safeTerminate("REGISTRATION_ERROR", "ASYNC_ALREADY_COMPLETED");
                return;
            }
            while (true) {
                LifecycleState current = state.get();
                if (current.phase() == Phase.TERMINAL || current.contains(context)) {
                    return;
                }
                LifecycleState claimed = current.claim(context);
                if (state.compareAndSet(current, claimed)) {
                    try {
                        context.addListener(listenerFor(claimed.generation()));
                    } catch (RuntimeException e) {
                        safeTerminate(response, claimed.generation(), "REGISTRATION_ERROR", e.getClass().getName());
                    }
                    return;
                }
            }
        }

        private void registerRestartedContext(AsyncEvent event, int previousGeneration) {
            AsyncContext context = event.getAsyncContext();
            HttpServletResponse terminalResponse = resolveResponse(event);
            if (context == null) {
                safeTerminate(terminalResponse, previousGeneration, "REGISTRATION_ERROR", "ASYNC_ALREADY_COMPLETED");
                return;
            }
            while (true) {
                LifecycleState current = state.get();
                if (current.phase() == Phase.TERMINAL || current.generation() != previousGeneration) {
                    return;
                }
                LifecycleState claimed = current.claim(context);
                if (state.compareAndSet(current, claimed)) {
                    try {
                        context.addListener(listenerFor(claimed.generation()));
                    } catch (RuntimeException e) {
                        safeTerminate(terminalResponse, claimed.generation(), "REGISTRATION_ERROR", e.getClass().getName());
                    }
                    return;
                }
            }
        }

        private void safeTerminate(AsyncEvent event, int registrationGeneration, String outcome, String errorType) {
            safeTerminate(resolveResponse(event), registrationGeneration, outcome, errorType);
        }

        private void safeTerminate(String outcome, String errorType) {
            safeTerminate(response, -1, outcome, errorType);
        }

        private void safeTerminate(HttpServletResponse terminalResponse, int registrationGeneration,
                                   String outcome, String errorType) {
            try {
                terminate(terminalResponse, registrationGeneration, outcome, errorType);
            } catch (RuntimeException e) {
                LOGGER.warn("Failed to finalize access log lifecycle", e);
            }
        }

        private HttpServletResponse resolveResponse(AsyncEvent event) {
            try {
                ServletResponse asyncResponse = event.getAsyncContext().getResponse();
                if (asyncResponse instanceof HttpServletResponse httpServletResponse) {
                    return httpServletResponse;
                }
            } catch (RuntimeException e) {
                LOGGER.debug("Failed to resolve async response", e);
            }
            return response;
        }

        private void terminate(HttpServletResponse terminalResponse, int registrationGeneration,
                               String outcome, String errorType) {
            while (true) {
                LifecycleState current = state.get();
                if (current.phase() == Phase.TERMINAL
                        || (registrationGeneration >= 0 && current.generation() != registrationGeneration)) {
                    return;
                }
                if (state.compareAndSet(current, current.terminal())) {
                    long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
                    if (shouldLogAccess(request)) {
                        logAccess(request, terminalResponse, duration, outcome, errorType);
                    }
                    return;
                }
            }
        }
    }

    private enum Phase {
        NEW,
        REGISTERED,
        TERMINAL
    }

    private record LifecycleState(Phase phase, int generation, List<AsyncContext> claimedContexts) {
        private static LifecycleState initial() {
            return new LifecycleState(Phase.NEW, 0, List.of());
        }

        private boolean contains(AsyncContext candidate) {
            return claimedContexts.stream().anyMatch(existing -> existing == candidate);
        }

        private LifecycleState claim(AsyncContext context) {
            List<AsyncContext> claimed = contains(context)
                    ? claimedContexts : new ArrayList<>(claimedContexts);
            if (!contains(context)) {
                claimed.add(context);
            }
            return new LifecycleState(Phase.REGISTERED, generation + 1, List.copyOf(claimed));
        }

        private LifecycleState terminal() {
            return new LifecycleState(Phase.TERMINAL, generation, claimedContexts);
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
    private void logAccess(HttpServletRequest request, HttpServletResponse response, long durationMs, String outcome, String errorType) {
        try {
            String ip = sanitize(getClientIp(request));
            String method = sanitize(request.getMethod());
            String uri = sanitize(request.getRequestURI());
            int statusCode = response.getStatus();
            String userAgent = sanitize(request.getHeader("User-Agent"));

            // 查询参数可能包含令牌、口令等敏感信息，只记录请求路径。
            logAccessByStatus(ip, method, uri, statusCode, durationMs, userAgent != null ? userAgent : "Unknown", outcome, errorType);
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
    private void logAccessByStatus(String ip, String method, String uri, int statusCode, long durationMs, String userAgent, String outcome, String errorType) {
        boolean isSlow = durationMs > slowThresholdMs;

        // 使用参数化日志，防止日志注入攻击
        String message = "IP=[{}], Method=[{}], URI=[{}], Status=[{}], Outcome=[{}], ErrorType=[{}], Duration=[{}ms], UserAgent=[{}]";
        Object[] args = new Object[]{ip, method, uri, statusCode, outcome, errorType, durationMs, userAgent};

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
     * 获取容器提供的直连对端 IP。
     *
     * <p>转发头仅应由已建立可信边界的容器改写为 remoteAddr 后使用。</p>
     *
     * @param request HTTP 请求
     * @return 客户端真实 IP
     */
    private String getClientIp(HttpServletRequest request) {
        return IpUtils.resolveClientIp(request::getRemoteAddr);
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
