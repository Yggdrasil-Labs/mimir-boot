package com.yggdrasil.labs.web.interceptor;

import com.yggdrasil.labs.common.constant.CommonConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * Trace 拦截器
 *
 * <p>功能说明：</p>
 * <ul>
 * <li>自动生成或从请求头获取 traceId</li>
 * <li>将 traceId 设置到 MDC 和响应头</li>
 * <li>支持与 Micrometer Tracing 集成（当检测到 Tracer 时自动禁用）</li>
 * </ul>
 *
 * <p>注意：</p>
 * <ul>
 * <li>如果 classpath 中存在 Micrometer Tracer，此拦截器将被禁用</li>
 * <li>由 starter-trace 模块接管 Trace 逻辑</li>
 * </ul>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@Slf4j
public class TraceInterceptor implements HandlerInterceptor {

    /**
     * 请求处理前
     * <p>
     * 设置 traceId：从请求头获取或生成新的
     * </p>
     *
     * @param request  请求对象
     * @param response 响应对象
     * @param handler  处理器
     * @return 是否继续处理
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 检查请求头中是否有 traceId
        String headerTraceId = request.getHeader(CommonConstants.TRACE_ID_HEADER);
        boolean hasHeaderTraceId = StringUtils.hasText(headerTraceId);

        // 获取或生成 traceId
        String traceId = getOrGenerateTraceId(request);

        // 设置到 MDC
        // 如果请求头中有 traceId，或者 MDC 中还没有 traceId，则更新 MDC
        if (hasHeaderTraceId || !StringUtils.hasText(org.slf4j.MDC.get("traceId"))) {
            org.slf4j.MDC.put("traceId", traceId);
        }

        // 将 traceId 添加到响应头
        response.setHeader(CommonConstants.TRACE_ID_HEADER, traceId);

        return true;
    }

    /**
     * 请求处理后
     * <p>
     * 注意：不清理 MDC，因为 traceId 可能在其他地方仍在使用
     * 由 WebInterceptor 统一清理
     * </p>
     *
     * @param request  请求对象
     * @param response 响应对象
     * @param handler  处理器
     * @param ex       异常（如果有）
     */
    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {
        // TraceInterceptor 不清理 MDC，由 WebInterceptor 统一清理
        // 这样可以确保 traceId 在整个请求生命周期内可用
    }

    /**
     * 获取或生成 traceId
     * <p>
     * 优先级：
     * 1. 从请求头 X-Trace-Id 获取
     * 2. 从 MDC 获取（可能已被其他组件设置）
     * 3. 生成新的 UUID（去除连字符）
     * </p>
     *
     * @param request HTTP 请求
     * @return traceId
     */
    private String getOrGenerateTraceId(HttpServletRequest request) {
        // 优先从请求头获取 traceId
        String traceId = request.getHeader(CommonConstants.TRACE_ID_HEADER);
        if (StringUtils.hasText(traceId)) {
            return traceId;
        }

        // 从 MDC 获取（可能已被其他组件设置，如 Micrometer Tracing）
        traceId = org.slf4j.MDC.get("traceId");
        if (StringUtils.hasText(traceId)) {
            return traceId;
        }

        // 生成新的 traceId（使用 UUID，去除连字符）
        return UUID.randomUUID().toString().replace("-", "");
    }
}

