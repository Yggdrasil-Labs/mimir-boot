package com.yggdrasil.labs.web.interceptor;

import com.yggdrasil.labs.common.util.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * Web 拦截器
 *
 * <p>功能说明：</p>
 * <ul>
 * <li>自动设置请求上下文信息（IP 等）</li>
 * <li>清理请求上下文（防止内存泄漏）</li>
 * </ul>
 *
 * <p>注意：</p>
 * <ul>
 * <li>Trace 相关逻辑已封装在独立的 TraceInterceptor 中</li>
 * <li>此拦截器主要负责非 Trace 的上下文信息处理</li>
 * </ul>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@Slf4j
public class WebInterceptor implements AsyncHandlerInterceptor {
    private static final String IP = "ip";
    private static final String IP_MDC_STACK_ATTRIBUTE = WebInterceptor.class.getName() + ".ipMdcStack";
    /**
     * 请求处理前
     * <p>
     * 设置请求上下文信息：IP 等
     * </p>
     *
     * @param request  请求对象
     * @param response 响应对象
     * @param handler  处理器
     * @return 是否继续处理
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 提取并设置客户端 IP
        String clientIp = getClientIp(request);
        ipMdcStack(request).push(Optional.ofNullable(org.slf4j.MDC.get(IP)));
        if (StringUtils.hasText(clientIp)) {
            org.slf4j.MDC.put(IP, clientIp);
        }

        return true;
    }

    /**
     * 请求处理后
     * <p>
     * 仅恢复此拦截器写入前的 IP，防止影响无关 MDC 上下文。
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
        restorePreviousIp(request);
    }

    @Override
    public void afterConcurrentHandlingStarted(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {
        restorePreviousIp(request);
    }

    private void restorePreviousIp(HttpServletRequest request) {
        Deque<Optional<String>> stack = ipMdcStackOrNull(request);
        if (stack == null || stack.isEmpty()) {
            return;
        }
        restoreMdcValue(IP, stack.pop());
        if (stack.isEmpty()) {
            request.removeAttribute(IP_MDC_STACK_ATTRIBUTE);
        }
    }

    /**
     * 获取直连对端 IP。转发头的可信边界由容器或显式调用方负责。
     *
     * @param request HTTP 请求
     * @return 客户端真实 IP
     */
    private String getClientIp(HttpServletRequest request) {
        return IpUtils.resolveClientIp(request::getRemoteAddr);
    }

    @SuppressWarnings("unchecked")
    private Deque<Optional<String>> ipMdcStack(HttpServletRequest request) {
        Deque<Optional<String>> stack = ipMdcStackOrNull(request);
        if (stack == null) {
            stack = new ArrayDeque<>();
            request.setAttribute(IP_MDC_STACK_ATTRIBUTE, stack);
        }
        return stack;
    }

    @SuppressWarnings("unchecked")
    private Deque<Optional<String>> ipMdcStackOrNull(HttpServletRequest request) {
        return (Deque<Optional<String>>) request.getAttribute(IP_MDC_STACK_ATTRIBUTE);
    }

    private void restoreMdcValue(String key, Optional<String> previousValue) {
        if (previousValue.isPresent()) {
            org.slf4j.MDC.put(key, previousValue.get());
        } else {
            org.slf4j.MDC.remove(key);
        }
    }
}
