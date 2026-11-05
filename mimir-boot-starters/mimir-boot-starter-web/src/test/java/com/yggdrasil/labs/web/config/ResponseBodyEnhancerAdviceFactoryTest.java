package com.yggdrasil.labs.web.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod;

import com.yggdrasil.labs.common.response.R;
import com.yggdrasil.labs.web.advice.ResponseBodyEnhancer;

/** 验证内部 Object Advice 在调用 R 泛型增强器前保留运行值与元信息边界。 */
class ResponseBodyEnhancerAdviceFactoryTest {

    private final RecordingEnhancer delegate = new RecordingEnhancer();

    private final ResponseBodyEnhancerAdviceFactory.MvcResponseBodyEnhancer advice =
            new ResponseBodyEnhancerAdviceFactory().create(delegate);

    private final ServerHttpRequest request =
            new ServletServerHttpRequest(new MockHttpServletRequest());

    private final ServerHttpResponse response =
            new ServletServerHttpResponse(new MockHttpServletResponse());

    @ParameterizedTest
    @MethodSource("nonResponseBodies")
    void preservesNonRAndNullWithoutCallingGenericDelegate(Object body) throws Exception {
        MethodParameter returnType = runtimeReturnType();
        assertThat(advice.supports(returnType, MappingJackson2HttpMessageConverter.class)).isTrue();

        assertThat(
                        advice.beforeBodyWrite(
                                body,
                                returnType,
                                MediaType.APPLICATION_JSON,
                                MappingJackson2HttpMessageConverter.class,
                                request,
                                response))
                .isSameAs(body);
        assertThat(delegate.bodyWriteCount).isZero();
    }

    static Stream<Object> nonResponseBodies() {
        return Stream.of("payload", Map.of("data", "payload"), new Object(), null);
    }

    @Test
    void delegatesROnceWithOriginalMvcArguments() throws Exception {
        MethodParameter returnType = runtimeReturnType();
        R<String> body = R.success("payload");

        assertThat(advice.supports(returnType, MappingJackson2HttpMessageConverter.class)).isTrue();
        assertThat(delegate.supportsReturnType).isSameAs(returnType);
        assertThat(delegate.supportsConverter).isEqualTo(MappingJackson2HttpMessageConverter.class);
        assertThat(
                        advice.beforeBodyWrite(
                                body,
                                returnType,
                                MediaType.APPLICATION_JSON,
                                MappingJackson2HttpMessageConverter.class,
                                request,
                                response))
                .isSameAs(body);

        assertThat(delegate.bodyWriteCount).isEqualTo(1);
        assertThat(delegate.bodyReturnType).isSameAs(returnType);
        assertThat(delegate.bodyReturnType.getContainingClass()).isEqualTo(ObjectAdvice.class);
        assertThat(delegate.bodyReturnType.getGenericParameterType()).isEqualTo(Object.class);
        assertThat(delegate.contentType).isSameAs(MediaType.APPLICATION_JSON);
        assertThat(delegate.bodyConverter).isEqualTo(MappingJackson2HttpMessageConverter.class);
        assertThat(delegate.request).isSameAs(request);
        assertThat(delegate.response).isSameAs(response);
    }

    @Test
    void doesNotOverrideConsumerSupportsRejection() throws Exception {
        delegate.supports = false;
        MethodParameter returnType = runtimeReturnType();

        assertThat(advice.supports(returnType, MappingJackson2HttpMessageConverter.class))
                .isFalse();
        assertThat(delegate.supportsReturnType).isSameAs(returnType);
        assertThat(delegate.supportsConverter).isEqualTo(MappingJackson2HttpMessageConverter.class);
        assertThat(delegate.bodyWriteCount).isZero();
    }

    private static MethodParameter runtimeReturnType() throws Exception {
        ObjectAdvice bean = new ObjectAdvice();
        return new ServletInvocableHandlerMethod(bean, ObjectAdvice.class.getMethod("response"))
                .getReturnValueType(bean.response());
    }

    @RestControllerAdvice
    static class ObjectAdvice {

        public Object response() {
            return ResponseEntity.ok(R.success("payload"));
        }
    }

    private static final class RecordingEnhancer extends ResponseBodyEnhancer {

        private boolean supports = true;
        private int bodyWriteCount;
        private MethodParameter supportsReturnType;
        private Class<? extends HttpMessageConverter<?>> supportsConverter;
        private MethodParameter bodyReturnType;
        private MediaType contentType;
        private Class<? extends HttpMessageConverter<?>> bodyConverter;
        private ServerHttpRequest request;
        private ServerHttpResponse response;

        private RecordingEnhancer() {
            super(new WebProperties());
        }

        @Override
        public boolean supports(
                MethodParameter returnType,
                Class<? extends HttpMessageConverter<?>> converterType) {
            supportsReturnType = returnType;
            supportsConverter = converterType;
            return supports;
        }

        @Override
        public R<?> beforeBodyWrite(
                R<?> body,
                MethodParameter returnType,
                MediaType selectedContentType,
                Class<? extends HttpMessageConverter<?>> selectedConverterType,
                ServerHttpRequest request,
                ServerHttpResponse response) {
            bodyWriteCount++;
            bodyReturnType = returnType;
            contentType = selectedContentType;
            bodyConverter = selectedConverterType;
            this.request = request;
            this.response = response;
            return body;
        }
    }
}
