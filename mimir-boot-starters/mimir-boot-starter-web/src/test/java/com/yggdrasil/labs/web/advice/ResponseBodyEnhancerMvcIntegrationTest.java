package com.yggdrasil.labs.web.advice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import com.yggdrasil.labs.common.constant.HttpHeaderConstants;
import com.yggdrasil.labs.common.response.R;
import com.yggdrasil.labs.exception.handler.MimirExceptionHandler;

/**
 * 验证响应增强器通过真实 Spring MVC advice 链处理成功和异常响应。
 *
 * @author Yggdrasil Labs
 * @since 2.2.1
 */
@SpringBootTest(
        classes = ResponseBodyEnhancerMvcIntegrationTest.TestApplication.class,
        properties = "logging.level.root=OFF")
@AutoConfigureMockMvc
class ResponseBodyEnhancerMvcIntegrationTest {

    private static final String TRACE_ID = "mvc-response-trace";

    @Autowired private MockMvc mockMvc;

    @Autowired private ResponseBodyEnhancer responseBodyEnhancer;

    @Autowired private ApplicationContext applicationContext;

    @Autowired private CountingExceptionResponseBodyAdvice countingExceptionResponseBodyAdvice;

    @BeforeEach
    void resetCountingExceptionResponseBodyAdvice() {
        countingExceptionResponseBodyAdvice.reset();
    }

    @Test
    void supportsExceptionHandlerObjectReturnType() throws Exception {
        Method method =
                MimirExceptionHandler.class.getMethod(
                        "handleException",
                        Exception.class,
                        jakarta.servlet.http.HttpServletRequest.class);

        ResponseBodyAdvice<?> responseBodyAdvice =
                applicationContext.getBean("responseBodyEnhancerAdvice", ResponseBodyAdvice.class);

        org.junit.jupiter.api.Assertions.assertTrue(
                responseBodyAdvice.supports(
                        new MethodParameter(method, -1),
                        MappingJackson2HttpMessageConverter.class));
    }

    @Test
    void preservesResponseBodyAdviceSourceContract() {
        ResponseBodyAdvice<R<?>> advice = responseBodyEnhancer;

        assertThat(advice).isSameAs(responseBodyEnhancer);
    }

    @Test
    void fillsTraceIdForControllerResponse() throws Exception {
        mockMvc.perform(
                        get("/response-enhancer/success")
                                .header(HttpHeaderConstants.TRACE_ID_HEADER, TRACE_ID))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaderConstants.TRACE_ID_HEADER, TRACE_ID))
                .andExpect(jsonPath("$.data").value("payload"))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID));
    }

    @Test
    void fillsTraceIdForResponseEntityBody() throws Exception {
        mockMvc.perform(
                        get("/response-enhancer/response-entity")
                                .header(HttpHeaderConstants.TRACE_ID_HEADER, TRACE_ID))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaderConstants.TRACE_ID_HEADER, TRACE_ID))
                .andExpect(jsonPath("$.data").value("response-entity"))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID));
    }

    @Test
    void fillsTraceIdForDefaultExceptionResponse() throws Exception {
        mockMvc.perform(
                        get("/response-enhancer/failure")
                                .header(HttpHeaderConstants.TRACE_ID_HEADER, TRACE_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string(HttpHeaderConstants.TRACE_ID_HEADER, TRACE_ID))
                .andExpect(jsonPath("$.code").isNotEmpty())
                .andExpect(jsonPath("$.traceId").value(TRACE_ID));
    }

    @Test
    void executesDownstreamExceptionAdviceExactlyOnce() throws Exception {
        mockMvc.perform(
                        get("/response-enhancer/failure")
                                .header(HttpHeaderConstants.TRACE_ID_HEADER, TRACE_ID))
                .andExpect(status().isInternalServerError());

        assertThat(countingExceptionResponseBodyAdvice.invocationCount()).isEqualTo(1);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({ResponseController.class, CountingExceptionResponseBodyAdvice.class})
    static class TestApplication {}

    @RestController
    static class ResponseController {

        @GetMapping("/response-enhancer/success")
        R<String> success() {
            return R.success("payload");
        }

        @GetMapping("/response-enhancer/response-entity")
        ResponseEntity<R<String>> responseEntity() {
            return ResponseEntity.ok(R.success("response-entity"));
        }

        @GetMapping("/response-enhancer/failure")
        R<String> failure() {
            throw new IllegalStateException("failure");
        }
    }

    @RestControllerAdvice
    static class CountingExceptionResponseBodyAdvice implements ResponseBodyAdvice<Object> {

        private final AtomicInteger invocationCount = new AtomicInteger();

        @Override
        public boolean supports(
                MethodParameter returnType,
                Class<? extends HttpMessageConverter<?>> converterType) {
            return returnType.getContainingClass() == MimirExceptionHandler.class;
        }

        @Override
        public Object beforeBodyWrite(
                Object body,
                MethodParameter returnType,
                MediaType selectedContentType,
                Class<? extends HttpMessageConverter<?>> selectedConverterType,
                ServerHttpRequest request,
                ServerHttpResponse response) {
            invocationCount.incrementAndGet();
            return body;
        }

        int invocationCount() {
            return invocationCount.get();
        }

        void reset() {
            invocationCount.set(0);
        }
    }
}
