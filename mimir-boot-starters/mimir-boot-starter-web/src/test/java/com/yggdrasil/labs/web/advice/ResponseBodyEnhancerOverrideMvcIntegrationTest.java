package com.yggdrasil.labs.web.advice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.yggdrasil.labs.common.constant.HttpHeaderConstants;
import com.yggdrasil.labs.common.response.R;
import com.yggdrasil.labs.web.config.WebProperties;

/**
 * 验证下游替换响应增强器后，MVC advice 链仍委托到替换实现。
 *
 * @author Yggdrasil Labs
 * @since 2.2.1
 */
@SpringBootTest(
        classes = ResponseBodyEnhancerOverrideMvcIntegrationTest.TestApplication.class,
        properties = "logging.level.root=OFF")
@AutoConfigureMockMvc
class ResponseBodyEnhancerOverrideMvcIntegrationTest {

    private static final String TRACE_ID = "mvc-custom-response-trace";

    @Autowired private MockMvc mockMvc;

    @Autowired private ResponseBodyEnhancer responseBodyEnhancer;

    @AfterEach
    void resetResponseBodyEnhancer() {
        CountingResponseBodyEnhancer countingResponseBodyEnhancer =
                (CountingResponseBodyEnhancer) responseBodyEnhancer;
        countingResponseBodyEnhancer.reset();
        countingResponseBodyEnhancer.setSupportsResponse(true);
    }

    @Test
    void delegatesMvcAdviceToCustomResponseBodyEnhancer() throws Exception {
        mockMvc.perform(
                        get("/response-enhancer-override/success")
                                .header(HttpHeaderConstants.TRACE_ID_HEADER, TRACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId").value(TRACE_ID));

        assertThat(responseBodyEnhancer).isInstanceOf(CountingResponseBodyEnhancer.class);
        assertThat(((CountingResponseBodyEnhancer) responseBodyEnhancer).invocationCount())
                .isEqualTo(1);
    }

    @Test
    void honoursCustomResponseBodyEnhancerSupports() throws Exception {
        CountingResponseBodyEnhancer countingResponseBodyEnhancer =
                (CountingResponseBodyEnhancer) responseBodyEnhancer;
        countingResponseBodyEnhancer.setSupportsResponse(false);

        mockMvc.perform(
                        get("/response-enhancer-override/success")
                                .header(HttpHeaderConstants.TRACE_ID_HEADER, TRACE_ID))
                .andExpect(status().isOk());

        assertThat(countingResponseBodyEnhancer.invocationCount()).isZero();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({ResponseController.class, CustomResponseBodyEnhancerConfiguration.class})
    static class TestApplication {}

    @RestController
    static class ResponseController {

        @GetMapping("/response-enhancer-override/success")
        R<String> success() {
            return R.success("payload");
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CustomResponseBodyEnhancerConfiguration {

        @Bean
        ResponseBodyEnhancer responseBodyEnhancer(WebProperties webProperties) {
            return new CountingResponseBodyEnhancer(webProperties);
        }
    }

    private static final class CountingResponseBodyEnhancer extends ResponseBodyEnhancer {

        private final AtomicInteger invocationCount = new AtomicInteger();

        private final AtomicBoolean supportsResponse = new AtomicBoolean(true);

        private CountingResponseBodyEnhancer(WebProperties webProperties) {
            super(webProperties);
        }

        @Override
        public boolean supports(
                MethodParameter returnType,
                Class<? extends HttpMessageConverter<?>> converterType) {
            return supportsResponse.get() && super.supports(returnType, converterType);
        }

        @Override
        public R<?> beforeBodyWrite(
                R<?> body,
                MethodParameter returnType,
                MediaType selectedContentType,
                Class<? extends HttpMessageConverter<?>> selectedConverterType,
                ServerHttpRequest request,
                ServerHttpResponse response) {
            invocationCount.incrementAndGet();
            return super.beforeBodyWrite(
                    body,
                    returnType,
                    selectedContentType,
                    selectedConverterType,
                    request,
                    response);
        }

        private int invocationCount() {
            return invocationCount.get();
        }

        private void reset() {
            invocationCount.set(0);
        }

        private void setSupportsResponse(boolean supportsResponse) {
            this.supportsResponse.set(supportsResponse);
        }
    }
}
