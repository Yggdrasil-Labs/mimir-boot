package com.yggdrasil.labs.web.advice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Min;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.yggdrasil.labs.common.constant.CommonConstants;
import com.yggdrasil.labs.common.constant.HttpHeaderConstants;
import com.yggdrasil.labs.common.exception.ErrorCode;
import com.yggdrasil.labs.common.response.R;

/** 验证既有八类 ResponseEntity 异常分支经真实 Exception Advice 后保留响应追踪。 */
@SpringBootTest(
        classes = ExistingExceptionTraceMvcIntegrationTest.TestApplication.class,
        properties = "logging.level.root=OFF")
@AutoConfigureMockMvc
class ExistingExceptionTraceMvcIntegrationTest {

    private static final String TRACE_ID = "existing-exception-trace";

    @Autowired private MockMvc mockMvc;
    private Map<String, String> previousMdc;

    @BeforeEach
    void setTraceContext() {
        previousMdc = MDC.getCopyOfContextMap();
        // 406/415 在 HandlerMapping 阶段发生，使用此前已存在的追踪上下文。
        MDC.put(CommonConstants.TRACE_ID, TRACE_ID);
    }

    @AfterEach
    void restoreTraceContext() {
        if (previousMdc == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(previousMdc);
        }
    }

    @Test
    void enhancesNativeMethodValidationFailure() throws Exception {
        assertExceptionResponse(
                get("/existing-exception-trace/method-validation").param("count", "0"),
                HandlerMethodValidationException.class,
                400,
                ErrorCode.PARAM_INVALID);
    }

    @Test
    void enhancesServiceConstraintViolation() throws Exception {
        assertExceptionResponse(
                get("/existing-exception-trace/service-validation"),
                ConstraintViolationException.class,
                400,
                ErrorCode.PARAM_INVALID);
    }

    @Test
    void enhancesMissingRequestHeader() throws Exception {
        assertExceptionResponse(
                get("/existing-exception-trace/required-header"),
                MissingRequestHeaderException.class,
                400,
                ErrorCode.PARAM_MISSING);
    }

    @Test
    void enhancesMissingPathVariable() throws Exception {
        assertExceptionResponse(
                get("/existing-exception-trace/missing-path-variable"),
                MissingPathVariableException.class,
                500,
                ErrorCode.SYSTEM_ERROR);
    }

    @Test
    void enhancesNotAcceptableWhenErrorJsonIsWritable() throws Exception {
        assertExceptionResponse(
                get("/existing-exception-trace/plain-response"),
                HttpMediaTypeNotAcceptableException.class,
                406,
                ErrorCode.OPERATION_NOT_ALLOWED);
    }

    @Test
    void enhancesUnsupportedRequestMediaType() throws Exception {
        assertExceptionResponse(
                post("/existing-exception-trace/json-request")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("payload"),
                HttpMediaTypeNotSupportedException.class,
                415,
                ErrorCode.OPERATION_NOT_ALLOWED);
    }

    @Test
    void enhancesMissingStaticResource() throws Exception {
        assertExceptionResponse(
                get("/existing-exception-trace/absent-resource.css"),
                NoResourceFoundException.class,
                404,
                ErrorCode.DATA_NOT_FOUND);
    }

    private void assertExceptionResponse(
            MockHttpServletRequestBuilder request,
            Class<? extends Exception> exceptionType,
            int expectedStatus,
            ErrorCode errorCode)
            throws Exception {
        mockMvc.perform(
                        request.accept(MediaType.APPLICATION_JSON)
                                .header(HttpHeaderConstants.TRACE_ID_HEADER, TRACE_ID))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(errorCode.getCode()))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID))
                .andExpect(
                        result ->
                                assertThat(result.getResolvedException())
                                        .isExactlyInstanceOf(exceptionType));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({ResponseController.class, ConstraintService.class})
    static class TestApplication {}

    @RestController
    static class ResponseController {
        private final ConstraintService constraintService;

        ResponseController(ConstraintService constraintService) {
            this.constraintService = constraintService;
        }

        @GetMapping("/existing-exception-trace/method-validation")
        R<Integer> methodValidation(@RequestParam("count") @Min(1) int count) {
            return R.success(count);
        }

        @GetMapping("/existing-exception-trace/service-validation")
        R<Integer> serviceValidation() {
            return R.success(constraintService.validate(0));
        }

        @GetMapping("/existing-exception-trace/required-header")
        R<String> requiredHeader(@RequestHeader("X-Required-Value") String value) {
            return R.success(value);
        }

        @GetMapping("/existing-exception-trace/missing-path-variable")
        R<String> missingPathVariable(@PathVariable("id") String id) {
            return R.success(id);
        }

        @GetMapping(
                value = "/existing-exception-trace/plain-response",
                produces = MediaType.TEXT_PLAIN_VALUE)
        String plainResponse() {
            return "plain";
        }

        @PostMapping(
                value = "/existing-exception-trace/json-request",
                consumes = MediaType.APPLICATION_JSON_VALUE)
        R<String> jsonRequest(@RequestBody Map<String, String> body) {
            return R.success(body.get("payload"));
        }

        @PostMapping(
                value = "/existing-exception-trace/upload",
                consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        R<String> upload(@RequestPart("file") MultipartFile file) {
            return R.success(file.getOriginalFilename());
        }
    }

    @Validated
    static class ConstraintService {
        public int validate(@Min(1) int count) {
            return count;
        }
    }
}
