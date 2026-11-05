package com.yggdrasil.labs.web.advice;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod;

import com.yggdrasil.labs.common.response.R;
import com.yggdrasil.labs.test.base.BaseUnitTest;
import com.yggdrasil.labs.web.config.WebProperties;

/**
 * 响应体增强器测试
 *
 * <p>测试 ResponseBodyEnhancer 的功能：
 *
 * <ul>
 *   <li>判断是否支持增强
 *   <li>自动填充 traceId
 *   <li>跳过已包含 traceId 的响应
 * </ul>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class ResponseBodyEnhancerTest extends BaseUnitTest {

    private ResponseBodyEnhancer responseBodyEnhancer;

    @Mock private WebProperties webProperties;

    @Mock private WebProperties.Response responseConfig;

    @Mock private MethodParameter returnType;

    @Mock private ServerHttpRequest request;

    @Mock private ServerHttpResponse response;

    @Override
    @BeforeEach
    public void setUp() {
        super.setUp();
        responseBodyEnhancer = new ResponseBodyEnhancer(webProperties);
    }

    @Override
    @AfterEach
    public void tearDown() {
        super.tearDown();
    }

    /** 测试支持 R 类型的响应 */
    @Test
    void testSupportsWithRType() throws Exception {
        // 设置配置：只需要 isEnabled()，不需要 isAutoFillTraceId()
        when(webProperties.getResponse()).thenReturn(responseConfig);
        when(responseConfig.isEnabled()).thenReturn(true);

        // 使用真实的 @RestController 类进行测试
        @RestController
        class TestController {
            public R<String> test() {
                return R.success("test");
            }
        }

        // 创建真实的 MethodParameter
        Method method = TestController.class.getMethod("test");
        MethodParameter realReturnType = new MethodParameter(method, -1);

        boolean supports =
                responseBodyEnhancer.supports(realReturnType, StringHttpMessageConverter.class);

        assertTrue(supports);
    }

    @Test
    void supportsObjectAdviceReturningResponseEntityAtRuntime() throws Exception {
        when(webProperties.getResponse()).thenReturn(responseConfig);
        when(responseConfig.isEnabled()).thenReturn(true);
        ObjectResponseAdvice advice = new ObjectResponseAdvice();
        Method method = ObjectResponseAdvice.class.getMethod("response");
        MethodParameter runtimeReturnType =
                new ServletInvocableHandlerMethod(advice, method)
                        .getReturnValueType(advice.response());

        assertEquals(Object.class, runtimeReturnType.getGenericParameterType());
        assertEquals(ResponseEntity.class, runtimeReturnType.getParameterType());
        assertEquals(ObjectResponseAdvice.class, runtimeReturnType.getContainingClass());
        assertTrue(
                responseBodyEnhancer.supports(runtimeReturnType, StringHttpMessageConverter.class));
    }

    @RestControllerAdvice
    static class ObjectResponseAdvice {

        public Object response() {
            return ResponseEntity.ok(R.success("payload"));
        }

        public ResponseEntity<R<String>> typedResponse() {
            return ResponseEntity.ok(R.success("payload"));
        }

        public ResponseEntity<String> stringResponse() {
            return ResponseEntity.ok("payload");
        }

        public ResponseEntity<Object> objectResponse() {
            return ResponseEntity.ok(R.success("payload"));
        }

        public ResponseEntity<?> wildcardResponse() {
            return ResponseEntity.ok(R.success("payload"));
        }

        @SuppressWarnings("rawtypes")
        public ResponseEntity rawResponse() {
            return ResponseEntity.ok(R.success("payload"));
        }

        public HttpEntity<R<String>> httpEntityResponse() {
            return new HttpEntity<>(R.success("payload"));
        }

        public Object objectHttpEntityResponse() {
            return new HttpEntity<>(R.success("payload"));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("responseTypeCandidates")
    void preservesResponseTypeCandidateBoundaries(
            String scenario, MethodParameter candidate, boolean expected) {
        when(webProperties.getResponse()).thenReturn(responseConfig);
        when(responseConfig.isEnabled()).thenReturn(true);

        assertEquals(
                expected,
                responseBodyEnhancer.supports(candidate, StringHttpMessageConverter.class));
    }

    static Stream<Arguments> responseTypeCandidates() throws Exception {
        ObjectResponseAdvice advice = new ObjectResponseAdvice();
        return Stream.of(
                Arguments.of("Object Advice 声明", declaredType(advice, "response"), true),
                Arguments.of("显式 ResponseEntity<R>", declaredType(advice, "typedResponse"), true),
                Arguments.of("原有 HttpEntity<R>", declaredType(advice, "httpEntityResponse"), true),
                Arguments.of("显式非 R", runtimeType(advice, "stringResponse"), false),
                Arguments.of("显式 Object 泛型", runtimeType(advice, "objectResponse"), false),
                Arguments.of("通配泛型", runtimeType(advice, "wildcardResponse"), false),
                Arguments.of("原始 ResponseEntity", runtimeType(advice, "rawResponse"), false),
                Arguments.of(
                        "Object 实际为 HttpEntity",
                        runtimeType(advice, "objectHttpEntityResponse"),
                        false),
                Arguments.of(
                        "Controller 未知泛型",
                        runtimeType(new ObjectResponseController(), "response"),
                        false),
                Arguments.of("普通类无注解", runtimeType(new PlainObjectResponse(), "response"), false),
                Arguments.of(
                        "非 REST Advice",
                        runtimeType(new PlainControllerAdvice(), "response"),
                        false),
                Arguments.of(
                        "合并 REST Advice 注解",
                        runtimeType(new ComposedResponseAdvice(), "response"),
                        true));
    }

    @Test
    void disabledEnhancerRejectsRuntimeObjectAdviceCandidate() throws Exception {
        when(webProperties.getResponse()).thenReturn(responseConfig);
        when(responseConfig.isEnabled()).thenReturn(false);

        assertFalse(
                responseBodyEnhancer.supports(
                        runtimeType(new ObjectResponseAdvice(), "response"),
                        StringHttpMessageConverter.class));
    }

    private static MethodParameter declaredType(Object bean, String name) throws Exception {
        return new MethodParameter(bean.getClass().getMethod(name), -1);
    }

    private static MethodParameter runtimeType(Object bean, String name) throws Exception {
        Method method = bean.getClass().getMethod(name);
        return new ServletInvocableHandlerMethod(bean, method)
                .getReturnValueType(method.invoke(bean));
    }

    static class PlainObjectResponse {

        public Object response() {
            return ResponseEntity.ok(R.success("payload"));
        }
    }

    @RestController
    static class ObjectResponseController extends PlainObjectResponse {}

    @ControllerAdvice
    static class PlainControllerAdvice extends PlainObjectResponse {}

    @Retention(RetentionPolicy.RUNTIME)
    @RestControllerAdvice
    @interface ComposedAdvice {}

    @ComposedAdvice
    static class ComposedResponseAdvice extends PlainObjectResponse {}

    /** 测试不支持非 R 类型的响应 */
    @Test
    void testSupportsWithNonRType() throws Exception {
        // 设置配置：只需要 isEnabled()，不需要 isAutoFillTraceId()
        when(webProperties.getResponse()).thenReturn(responseConfig);
        when(responseConfig.isEnabled()).thenReturn(true);

        @RestController
        class TestController {
            public String test() {
                return "test";
            }
        }

        // 创建真实的 MethodParameter
        Method method = TestController.class.getMethod("test");
        MethodParameter realReturnType = new MethodParameter(method, -1);

        boolean supports =
                responseBodyEnhancer.supports(realReturnType, StringHttpMessageConverter.class);

        assertFalse(supports);
    }

    /** 测试响应增强功能被禁用时不支持 */
    @Test
    void testSupportsWhenDisabled() throws Exception {
        when(webProperties.getResponse()).thenReturn(responseConfig);
        when(responseConfig.isEnabled()).thenReturn(false);

        @RestController
        class TestController {
            public R<String> test() {
                return R.success("test");
            }
        }

        // 创建真实的 MethodParameter
        Method method = TestController.class.getMethod("test");
        MethodParameter realReturnType = new MethodParameter(method, -1);

        boolean supports =
                responseBodyEnhancer.supports(realReturnType, StringHttpMessageConverter.class);

        assertFalse(supports);
    }

    /** 测试自动填充 traceId */
    @Test
    void testBeforeBodyWriteFillsTraceId() throws Exception {
        // 设置配置：只需要 isAutoFillTraceId()，不需要 isEnabled()
        when(webProperties.getResponse()).thenReturn(responseConfig);
        when(responseConfig.isAutoFillTraceId()).thenReturn(true);

        // 在 MDC 中设置 traceId
        String traceId = "test-trace-id-123";
        org.slf4j.MDC.put("traceId", traceId);

        R<String> responseBody = R.success("test data");

        R<?> result =
                responseBodyEnhancer.beforeBodyWrite(
                        responseBody,
                        returnType,
                        MediaType.APPLICATION_JSON,
                        StringHttpMessageConverter.class,
                        request,
                        response);

        assertNotNull(result);
        assertEquals(traceId, result.getTraceId());
        assertEquals("test data", result.getData());
    }

    /** 测试跳过已包含 traceId 的响应 */
    @Test
    void testBeforeBodyWriteSkipsExistingTraceId() throws Exception {
        // 设置配置：只需要 isAutoFillTraceId()，不需要 isEnabled()
        when(webProperties.getResponse()).thenReturn(responseConfig);
        when(responseConfig.isAutoFillTraceId()).thenReturn(true);

        // 在 MDC 中设置 traceId
        org.slf4j.MDC.put("traceId", "mdc-trace-id");

        // 响应体已包含 traceId
        R<String> responseBody = R.success("test data");
        responseBody.setTraceId("existing-trace-id");

        R<?> result =
                responseBodyEnhancer.beforeBodyWrite(
                        responseBody,
                        returnType,
                        MediaType.APPLICATION_JSON,
                        StringHttpMessageConverter.class,
                        request,
                        response);

        assertNotNull(result);
        // 验证 traceId 没有被覆盖
        assertEquals("existing-trace-id", result.getTraceId());
    }

    /** 测试 null 响应体 */
    @Test
    void testBeforeBodyWriteWithNullBody() {
        R<?> result =
                responseBodyEnhancer.beforeBodyWrite(
                        null,
                        returnType,
                        MediaType.APPLICATION_JSON,
                        StringHttpMessageConverter.class,
                        request,
                        response);

        assertNull(result);
    }

    /** 测试自动填充 traceId 被禁用 */
    @Test
    void testBeforeBodyWriteWhenAutoFillDisabled() throws Exception {
        when(webProperties.getResponse()).thenReturn(responseConfig);
        when(responseConfig.isAutoFillTraceId()).thenReturn(false);

        org.slf4j.MDC.put("traceId", "test-trace-id");

        R<String> responseBody = R.success("test data");

        R<?> result =
                responseBodyEnhancer.beforeBodyWrite(
                        responseBody,
                        returnType,
                        MediaType.APPLICATION_JSON,
                        StringHttpMessageConverter.class,
                        request,
                        response);

        assertNotNull(result);
        // 验证 traceId 没有被填充
        assertNull(result.getTraceId());
    }

    /** 测试从 requestId 获取 traceId（当 traceId 不存在时） */
    @Test
    void testBeforeBodyWriteUsesRequestId() throws Exception {
        // 设置配置：只需要 isAutoFillTraceId()，不需要 isEnabled()
        when(webProperties.getResponse()).thenReturn(responseConfig);
        when(responseConfig.isAutoFillTraceId()).thenReturn(true);

        // MDC 中没有 traceId，但有 requestId
        String requestId = "request-id-123";
        org.slf4j.MDC.put("requestId", requestId);

        R<String> responseBody = R.success("test data");

        R<?> result =
                responseBodyEnhancer.beforeBodyWrite(
                        responseBody,
                        returnType,
                        MediaType.APPLICATION_JSON,
                        StringHttpMessageConverter.class,
                        request,
                        response);

        assertNotNull(result);
        assertEquals(requestId, result.getTraceId());
    }

    /** 测试 MDC 中没有 traceId 和 requestId */
    @Test
    void testBeforeBodyWriteWithNoTraceId() {
        // 设置配置：只需要 isAutoFillTraceId()，不需要 isEnabled()
        when(webProperties.getResponse()).thenReturn(responseConfig);
        when(responseConfig.isAutoFillTraceId()).thenReturn(true);

        // MDC 为空
        R<String> responseBody = R.success("test data");

        R<?> result =
                responseBodyEnhancer.beforeBodyWrite(
                        responseBody,
                        returnType,
                        MediaType.APPLICATION_JSON,
                        StringHttpMessageConverter.class,
                        request,
                        response);

        assertNotNull(result);
        // 验证 traceId 为 null（因为没有可用的 traceId）
        assertNull(result.getTraceId());
    }
}
