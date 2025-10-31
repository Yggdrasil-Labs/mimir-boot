package com.yggdrasil.labs.web.advice;

import com.yggdrasil.labs.common.response.R;
import com.yggdrasil.labs.web.config.WebProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 响应体增强器测试
 *
 * <p>测试 ResponseBodyEnhancer 的功能：</p>
 * <ul>
 * <li>判断是否支持增强</li>
 * <li>自动填充 traceId</li>
 * <li>跳过已包含 traceId 的响应</li>
 * </ul>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class ResponseBodyEnhancerTest {

    private ResponseBodyEnhancer responseBodyEnhancer;

    @Mock
    private WebProperties webProperties;

    @Mock
    private WebProperties.Response responseConfig;

    @Mock
    private MethodParameter returnType;

    @Mock
    private ServerHttpRequest request;

    @Mock
    private ServerHttpResponse response;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        
        // 设置默认配置
        when(webProperties.getResponse()).thenReturn(responseConfig);
        when(responseConfig.isEnabled()).thenReturn(true);
        when(responseConfig.isAutoFillTraceId()).thenReturn(true);
        
        responseBodyEnhancer = new ResponseBodyEnhancer(webProperties);
        
        // 清理 MDC
        org.slf4j.MDC.clear();
    }

    @AfterEach
    void tearDown() {
        org.slf4j.MDC.clear();
    }

    /**
     * 测试支持 R 类型的响应
     */
    @Test
    void testSupportsWithRType() throws Exception {
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

        boolean supports = responseBodyEnhancer.supports(realReturnType, StringHttpMessageConverter.class);

        assertTrue(supports);
    }

    /**
     * 测试不支持非 R 类型的响应
     */
    @Test
    void testSupportsWithNonRType() throws Exception {
        @RestController
        class TestController {
            public String test() {
                return "test";
            }
        }

        // 创建真实的 MethodParameter
        Method method = TestController.class.getMethod("test");
        MethodParameter realReturnType = new MethodParameter(method, -1);

        boolean supports = responseBodyEnhancer.supports(realReturnType, StringHttpMessageConverter.class);

        assertFalse(supports);
    }

    /**
     * 测试响应增强功能被禁用时不支持
     */
    @Test
    void testSupportsWhenDisabled() throws Exception {
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

        boolean supports = responseBodyEnhancer.supports(realReturnType, StringHttpMessageConverter.class);

        assertFalse(supports);
    }

    /**
     * 测试自动填充 traceId
     */
    @Test
    void testBeforeBodyWriteFillsTraceId() throws Exception {
        // 在 MDC 中设置 traceId
        String traceId = "test-trace-id-123";
        org.slf4j.MDC.put("traceId", traceId);

        R<String> responseBody = R.success("test data");

        R<?> result = responseBodyEnhancer.beforeBodyWrite(
                responseBody,
                returnType,
                MediaType.APPLICATION_JSON,
                StringHttpMessageConverter.class,
                request,
                response
        );

        assertNotNull(result);
        assertEquals(traceId, result.getTraceId());
        assertEquals("test data", result.getData());
    }

    /**
     * 测试跳过已包含 traceId 的响应
     */
    @Test
    void testBeforeBodyWriteSkipsExistingTraceId() throws Exception {
        // 在 MDC 中设置 traceId
        org.slf4j.MDC.put("traceId", "mdc-trace-id");

        // 响应体已包含 traceId
        R<String> responseBody = R.success("test data");
        responseBody.setTraceId("existing-trace-id");

        R<?> result = responseBodyEnhancer.beforeBodyWrite(
                responseBody,
                returnType,
                MediaType.APPLICATION_JSON,
                StringHttpMessageConverter.class,
                request,
                response
        );

        assertNotNull(result);
        // 验证 traceId 没有被覆盖
        assertEquals("existing-trace-id", result.getTraceId());
    }

    /**
     * 测试 null 响应体
     */
    @Test
    void testBeforeBodyWriteWithNullBody() throws Exception {
        R<?> result = responseBodyEnhancer.beforeBodyWrite(
                null,
                returnType,
                MediaType.APPLICATION_JSON,
                StringHttpMessageConverter.class,
                request,
                response
        );

        assertNull(result);
    }

    /**
     * 测试自动填充 traceId 被禁用
     */
    @Test
    void testBeforeBodyWriteWhenAutoFillDisabled() throws Exception {
        when(responseConfig.isAutoFillTraceId()).thenReturn(false);

        org.slf4j.MDC.put("traceId", "test-trace-id");

        R<String> responseBody = R.success("test data");

        R<?> result = responseBodyEnhancer.beforeBodyWrite(
                responseBody,
                returnType,
                MediaType.APPLICATION_JSON,
                StringHttpMessageConverter.class,
                request,
                response
        );

        assertNotNull(result);
        // 验证 traceId 没有被填充
        assertNull(result.getTraceId());
    }

    /**
     * 测试从 requestId 获取 traceId（当 traceId 不存在时）
     */
    @Test
    void testBeforeBodyWriteUsesRequestId() throws Exception {
        // MDC 中没有 traceId，但有 requestId
        String requestId = "request-id-123";
        org.slf4j.MDC.put("requestId", requestId);

        R<String> responseBody = R.success("test data");

        R<?> result = responseBodyEnhancer.beforeBodyWrite(
                responseBody,
                returnType,
                MediaType.APPLICATION_JSON,
                StringHttpMessageConverter.class,
                request,
                response
        );

        assertNotNull(result);
        assertEquals(requestId, result.getTraceId());
    }

    /**
     * 测试 MDC 中没有 traceId 和 requestId
     */
    @Test
    void testBeforeBodyWriteWithNoTraceId() throws Exception {
        // MDC 为空
        R<String> responseBody = R.success("test data");

        R<?> result = responseBodyEnhancer.beforeBodyWrite(
                responseBody,
                returnType,
                MediaType.APPLICATION_JSON,
                StringHttpMessageConverter.class,
                request,
                response
        );

        assertNotNull(result);
        // 验证 traceId 为 null（因为没有可用的 traceId）
        assertNull(result.getTraceId());
    }
}

