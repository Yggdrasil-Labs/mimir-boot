package com.yggdrasil.labs.web.advice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import com.yggdrasil.labs.common.constant.HttpHeaderConstants;
import com.yggdrasil.labs.common.response.R;

/** 验证 Object 声明的局部异常 Advice 经原生 MVC 解包后仍有类型保护。 */
@SpringBootTest(
        classes = ResponseBodyEnhancerObjectAdviceMvcIntegrationTest.TestApplication.class,
        properties = "logging.level.root=OFF")
@AutoConfigureMockMvc
class ResponseBodyEnhancerObjectAdviceMvcIntegrationTest {

    private static final String TRACE_ID = "object-advice-trace";

    @Autowired private MockMvc mockMvc;

    @Autowired private ObservingAdvice observingAdvice;

    @BeforeEach
    void resetObservation() {
        observingAdvice.returnType = null;
        observingAdvice.body = null;
        observingAdvice.invocationCount = 0;
    }

    @Test
    void enhancesUnwrappedRAndPreservesRuntimeMetadataForDownstreamAdvice() throws Exception {
        mockMvc.perform(
                        get("/response-enhancer-object/r")
                                .header(HttpHeaderConstants.TRACE_ID_HEADER, TRACE_ID))
                .andExpect(status().isAccepted())
                .andExpect(header().string("X-Local-Advice", "preserved"))
                .andExpect(jsonPath("$.data").value("payload"))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID));

        assertThat(observingAdvice.invocationCount).isEqualTo(1);
        assertThat(observingAdvice.returnType.getParameterType()).isEqualTo(ResponseEntity.class);
        assertThat(observingAdvice.returnType.getGenericParameterType()).isEqualTo(Object.class);
        assertThat(observingAdvice.returnType.getContainingClass())
                .isEqualTo(ObjectResponseAdvice.class);
        assertThat(observingAdvice.body).isInstanceOf(R.class);
    }

    @Test
    void preservesExistingBodyTraceId() throws Exception {
        mockMvc.perform(
                        get("/response-enhancer-object/existing")
                                .header(HttpHeaderConstants.TRACE_ID_HEADER, TRACE_ID))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.traceId").value("body-trace"));
    }

    @Test
    void preservesStringBodyWithoutGenericBridgeCast() throws Exception {
        mockMvc.perform(
                        get("/response-enhancer-object/string")
                                .header(HttpHeaderConstants.TRACE_ID_HEADER, TRACE_ID))
                .andExpect(status().isAccepted())
                .andExpect(content().string("raw-payload"));

        assertThat(observingAdvice.body).isEqualTo("raw-payload");
    }

    @Test
    void preservesOtherWritableObjectWithoutGenericBridgeCast() throws Exception {
        mockMvc.perform(
                        get("/response-enhancer-object/map")
                                .header(HttpHeaderConstants.TRACE_ID_HEADER, TRACE_ID))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.payload").value("raw-map"))
                .andExpect(jsonPath("$.traceId").doesNotExist());

        assertThat(observingAdvice.body).isEqualTo(Map.of("payload", "raw-map"));
    }

    @Test
    void preservesNullBody() throws Exception {
        mockMvc.perform(
                        get("/response-enhancer-object/null")
                                .header(HttpHeaderConstants.TRACE_ID_HEADER, TRACE_ID))
                .andExpect(status().isAccepted())
                .andExpect(header().string("X-Local-Advice", "preserved"))
                .andExpect(content().string(""));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({ResponseController.class, ObjectResponseAdvice.class, ObservingAdvice.class})
    static class TestApplication {}

    @RestController
    static class ResponseController {

        @GetMapping("/response-enhancer-object/{kind}")
        R<String> response(@PathVariable("kind") String kind) {
            throw new LocalResponseException(kind);
        }
    }

    private static final class LocalResponseException extends RuntimeException {

        private LocalResponseException(String kind) {
            super(kind);
        }
    }

    @RestControllerAdvice
    @Order(Ordered.HIGHEST_PRECEDENCE)
    static class ObjectResponseAdvice {

        @ExceptionHandler(LocalResponseException.class)
        Object handle(LocalResponseException exception) {
            Object body =
                    switch (exception.getMessage()) {
                        case "r" -> R.success("payload");
                        case "existing" -> {
                            R<String> result = R.success("payload");
                            result.setTraceId("body-trace");
                            yield result;
                        }
                        case "string" -> "raw-payload";
                        case "map" -> Map.of("payload", "raw-map");
                        default -> null;
                    };
            return ResponseEntity.accepted().header("X-Local-Advice", "preserved").body(body);
        }
    }

    @RestControllerAdvice
    @Order(Ordered.LOWEST_PRECEDENCE)
    static class ObservingAdvice implements ResponseBodyAdvice<Object> {

        private MethodParameter returnType;
        private Object body;
        private int invocationCount;

        @Override
        public boolean supports(
                MethodParameter returnType,
                Class<? extends HttpMessageConverter<?>> converterType) {
            return returnType.getContainingClass() == ObjectResponseAdvice.class;
        }

        @Override
        public Object beforeBodyWrite(
                Object body,
                MethodParameter returnType,
                MediaType selectedContentType,
                Class<? extends HttpMessageConverter<?>> selectedConverterType,
                ServerHttpRequest request,
                ServerHttpResponse response) {
            this.returnType = returnType;
            this.body = body;
            invocationCount++;
            return body;
        }
    }
}
