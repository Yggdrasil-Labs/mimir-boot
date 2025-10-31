package com.yggdrasil.labs.web.advice;

import com.yggdrasil.labs.common.response.R;
import com.yggdrasil.labs.web.config.WebProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 响应体增强器
 *
 * <p>功能说明：</p>
 * <ul>
 * <li>自动为 R 响应对象填充 traceId</li>
 * <li>支持跳过已包含 traceId 的响应</li>
 * <li>仅处理返回类型为 R 的接口</li>
 * </ul>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class ResponseBodyEnhancer implements ResponseBodyAdvice<R<?>> {

    private final WebProperties webProperties;

    /**
     * 判断是否支持增强
     * <p>
     * 仅对返回类型为 R 的接口进行增强
     * </p>
     *
     * @param returnType    返回类型
     * @param converterType 转换器类型
     * @return 是否支持增强
     */
    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // 检查是否启用响应增强
        if (!webProperties.getResponse().isEnabled()) {
            return false;
        }

        // 检查是否为 R 类型
        Class<?> returnClass = returnType.getParameterType();
        if (!R.class.isAssignableFrom(returnClass)) {
            return false;
        }

        // 检查是否为 @RestController
        return AnnotatedElementUtils.hasAnnotation(returnType.getContainingClass(), RestController.class);
    }

    /**
     * 增强响应体
     * <p>
     * 自动为 R 响应对象填充 traceId
     * </p>
     *
     * @param body          响应体
     * @param returnType    返回类型
     * @param selectedContentType 选中的内容类型
     * @param selectedConverterType 选中的转换器类型
     * @param request       请求对象
     * @param response      响应对象
     * @return 增强后的响应体
     */
    @Override
    public R<?> beforeBodyWrite(
            R<?> body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {

        // 如果响应体为 null，直接返回
        if (body == null) {
            return null;
        }

        // 如果已禁用自动填充 traceId，直接返回
        if (!webProperties.getResponse().isAutoFillTraceId()) {
            return body;
        }

        // 如果响应已包含 traceId，跳过填充
        if (body.getTraceId() != null && !body.getTraceId().isEmpty()) {
            return body;
        }

        // 从 MDC 获取 traceId
        String traceId = getTraceId();
        if (traceId != null && !traceId.isEmpty()) {
            body.setTraceId(traceId);
        }

        return body;
    }

    /**
     * 从 MDC 获取 traceId
     * <p>
     * 优先从 MDC 的 traceId 获取，如果不存在则尝试从 requestId 获取
     * </p>
     *
     * @return traceId
     */
    private String getTraceId() {
        // 优先从 MDC 的 traceId 获取（Micrometer Tracing 自动注入）
        String traceId = org.slf4j.MDC.get("traceId");
        if (traceId != null && !traceId.isEmpty()) {
            return traceId;
        }

        // 如果不存在，尝试从 requestId 获取
        String requestId = org.slf4j.MDC.get("requestId");
        if (requestId != null && !requestId.isEmpty()) {
            return requestId;
        }

        return null;
    }
}

