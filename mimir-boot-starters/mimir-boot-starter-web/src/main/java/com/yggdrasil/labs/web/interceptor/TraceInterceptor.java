package com.yggdrasil.labs.web.interceptor;

import com.yggdrasil.labs.common.constant.CommonConstants;
import com.yggdrasil.labs.common.constant.HttpHeaderConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Trace 拦截器
 *
 * <p>功能说明：</p>
 * <ul>
 * <li>自动生成或从请求头获取 traceId</li>
 * <li>将 traceId 设置到 MDC 和响应头</li>
 * </ul>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@Slf4j
public class TraceInterceptor implements AsyncHandlerInterceptor {

    public static final String TRACE_ID = CommonConstants.TRACE_ID;
    private static final String MDC_STACK_ATTRIBUTE = TraceInterceptor.class.getName() + ".mdcStack";
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

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
        // 获取或生成 traceId
        String traceId = getOrGenerateTraceId(request);

        // 将 traceId 添加到响应头
        response.setHeader(HttpHeaderConstants.TRACE_ID_HEADER, traceId);

        mdcStack(request).push(new MdcState(
                org.slf4j.MDC.get(TRACE_ID), org.slf4j.MDC.get(CommonConstants.REQUEST_ID)));
        org.slf4j.MDC.put(TRACE_ID, traceId);
        org.slf4j.MDC.put(CommonConstants.REQUEST_ID, getOrGenerateRequestId(request));

        return true;
    }

    /**
     * 请求处理后
     * <p>
     * 仅恢复此拦截器写入前的 traceId 与 requestId，其他 MDC 键由其所有者负责。
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
        restorePreviousMdcState(request);
    }

    @Override
    public void afterConcurrentHandlingStarted(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {
        restorePreviousMdcState(request);
    }

    private void restorePreviousMdcState(HttpServletRequest request) {
        Deque<MdcState> stack = mdcStackOrNull(request);
        if (stack == null || stack.isEmpty()) {
            return;
        }
        MdcState previous = stack.pop();
        restoreMdcValue(TRACE_ID, previous.traceId());
        restoreMdcValue(CommonConstants.REQUEST_ID, previous.requestId());
        if (stack.isEmpty()) {
            request.removeAttribute(MDC_STACK_ATTRIBUTE);
        }
    }

    /**
     * 获取或生成 traceId
     * <p>
     * 优先级：
     * 1. 请求头存在且合法时直接使用
     * 2. 请求头存在但非法时生成新的 UUID，不回退 MDC
     * 3. 请求头缺失时使用 MDC 中的合法 traceId
     * 4. 请求头与 MDC 均无可用值时生成新的 UUID（去除连字符）
     * </p>
     *
     * @param request HTTP 请求
     * @return traceId
     */
    private String getOrGenerateTraceId(HttpServletRequest request) {
        // 请求头存在时必须先验证；非法值不允许回退并复用 MDC
        String traceId = request.getHeader(HttpHeaderConstants.TRACE_ID_HEADER);
        if (traceId != null) {
            return isValidTraceId(traceId) ? traceId : generateTraceId();
        }

        // 请求头缺失时才从 MDC 获取（可能已被其他组件设置）
        traceId = org.slf4j.MDC.get(TRACE_ID);
        if (isValidTraceId(traceId)) {
            return traceId;
        }

        return generateTraceId();
    }

    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String getOrGenerateRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(HttpHeaderConstants.REQUEST_ID_HEADER);
        return isValidTraceId(requestId) ? requestId : generateTraceId();
    }

    private boolean isValidTraceId(String traceId) {
        return StringUtils.hasText(traceId) && TRACE_ID_PATTERN.matcher(traceId).matches();
    }

    @SuppressWarnings("unchecked")
    private Deque<MdcState> mdcStack(HttpServletRequest request) {
        Deque<MdcState> stack = mdcStackOrNull(request);
        if (stack == null) {
            stack = new ArrayDeque<>();
            request.setAttribute(MDC_STACK_ATTRIBUTE, stack);
        }
        return stack;
    }

    @SuppressWarnings("unchecked")
    private Deque<MdcState> mdcStackOrNull(HttpServletRequest request) {
        return (Deque<MdcState>) request.getAttribute(MDC_STACK_ATTRIBUTE);
    }

    private void restoreMdcValue(String key, String previousValue) {
        if (previousValue == null) {
            org.slf4j.MDC.remove(key);
        } else {
            org.slf4j.MDC.put(key, previousValue);
        }
    }

    private record MdcState(String traceId, String requestId) {
    }
}
