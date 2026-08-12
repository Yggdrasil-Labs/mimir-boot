package com.yggdrasil.labs.web.interceptor;

import com.yggdrasil.labs.common.constant.CommonConstants;
import com.yggdrasil.labs.common.constant.HttpHeaderConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

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

    public static final String TRACE_ID = CommonConstants.TRACE_ID;
    private static final String TRACE_ID_MDC_STACK_ATTRIBUTE = TraceInterceptor.class.getName() + ".traceIdMdcStack";
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

        traceIdMdcStack(request).push(Optional.ofNullable(org.slf4j.MDC.get(TRACE_ID)));
        org.slf4j.MDC.put(TRACE_ID, traceId);

        return true;
    }

    /**
     * 请求处理后
     * <p>
     * 仅恢复此拦截器写入前的 traceId，其他 MDC 键由其所有者负责。
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
        Deque<Optional<String>> stack = traceIdMdcStackOrNull(request);
        if (stack == null || stack.isEmpty()) {
            return;
        }
        restoreMdcValue(TRACE_ID, stack.pop());
        if (stack.isEmpty()) {
            request.removeAttribute(TRACE_ID_MDC_STACK_ATTRIBUTE);
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

    private boolean isValidTraceId(String traceId) {
        return StringUtils.hasText(traceId) && TRACE_ID_PATTERN.matcher(traceId).matches();
    }

    @SuppressWarnings("unchecked")
    private Deque<Optional<String>> traceIdMdcStack(HttpServletRequest request) {
        Deque<Optional<String>> stack = traceIdMdcStackOrNull(request);
        if (stack == null) {
            stack = new ArrayDeque<>();
            request.setAttribute(TRACE_ID_MDC_STACK_ATTRIBUTE, stack);
        }
        return stack;
    }

    @SuppressWarnings("unchecked")
    private Deque<Optional<String>> traceIdMdcStackOrNull(HttpServletRequest request) {
        return (Deque<Optional<String>>) request.getAttribute(TRACE_ID_MDC_STACK_ATTRIBUTE);
    }

    private void restoreMdcValue(String key, Optional<String> previousValue) {
        if (previousValue.isPresent()) {
            org.slf4j.MDC.put(key, previousValue.get());
        } else {
            org.slf4j.MDC.remove(key);
        }
    }
}
