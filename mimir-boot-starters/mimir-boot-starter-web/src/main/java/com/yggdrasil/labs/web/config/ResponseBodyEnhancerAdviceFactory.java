package com.yggdrasil.labs.web.config;

import com.yggdrasil.labs.common.response.R;
import com.yggdrasil.labs.web.advice.ResponseBodyEnhancer;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 创建仅由 Web 自动配置提供的 MVC Advice。
 *
 * <p>Spring 6.1.21 的 ResponseBodyAdvice 支持通过 ControllerAdvice 同时接入正常和异常响应链。
 * 内部 Advice 保持非静态，避免组件扫描独立注册；工厂本身不作为配置类注册，避免配置解析器自动注册成员类。
 * 因此宽扫描不会绕过 Web 开关，也不会重复增强。</p>
 */
final class ResponseBodyEnhancerAdviceFactory {

    MvcResponseBodyEnhancer create(ResponseBodyEnhancer responseBodyEnhancer) {
        return new MvcResponseBodyEnhancer(responseBodyEnhancer);
    }

    @ControllerAdvice
    final class MvcResponseBodyEnhancer implements ResponseBodyAdvice<Object> {

        private final ResponseBodyEnhancer responseBodyEnhancer;

        MvcResponseBodyEnhancer(ResponseBodyEnhancer responseBodyEnhancer) {
            this.responseBodyEnhancer = responseBodyEnhancer;
        }

        @Override
        public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
            return responseBodyEnhancer.supports(returnType, converterType);
        }

        @Override
        public Object beforeBodyWrite(
                Object body,
                MethodParameter returnType,
                MediaType selectedContentType,
                Class<? extends HttpMessageConverter<?>> selectedConverterType,
                ServerHttpRequest request,
                ServerHttpResponse response) {
            if (!(body instanceof R<?> responseBody)) {
                return body;
            }
            return responseBodyEnhancer.beforeBodyWrite(
                    responseBody,
                    returnType,
                    selectedContentType,
                    selectedConverterType,
                    request,
                    response);
        }
    }
}
