package com.yggdrasil.labs.web.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

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
public class WebInterceptor implements HandlerInterceptor {

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
        if (StringUtils.hasText(clientIp)) {
            org.slf4j.MDC.put("ip", clientIp);
        }

        return true;
    }

    /**
     * 请求处理后
     * <p>
     * 清理请求上下文，防止内存泄漏
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
        // 清理 MDC 上下文
        org.slf4j.MDC.clear();
    }

    /**
     * 获取客户端真实 IP
     * <p>
     * 支持反向代理场景，按优先级检查以下请求头：
     * 1. X-Forwarded-For
     * 2. X-Real-IP
     * 3. Proxy-Client-IP
     * 4. WL-Proxy-Client-IP
     * 5. getRemoteAddr()
     * </p>
     *
     * @param request HTTP 请求
     * @return 客户端真实 IP
     */
    private String getClientIp(HttpServletRequest request) {
        // 检查 X-Forwarded-For 请求头（可能包含多个 IP，取第一个）
        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            int index = ip.indexOf(',');
            if (index != -1) {
                ip = ip.substring(0, index);
            }
            return ip.trim();
        }

        // 检查 X-Real-IP 请求头
        ip = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }

        // 检查 Proxy-Client-IP 请求头
        ip = request.getHeader("Proxy-Client-IP");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }

        // 检查 WL-Proxy-Client-IP 请求头
        ip = request.getHeader("WL-Proxy-Client-IP");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }

        // 使用 getRemoteAddr() 作为兜底
        return request.getRemoteAddr();
    }
}

