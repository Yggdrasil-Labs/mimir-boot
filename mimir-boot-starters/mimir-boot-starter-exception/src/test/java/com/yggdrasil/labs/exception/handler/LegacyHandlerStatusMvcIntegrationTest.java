package com.yggdrasil.labs.exception.handler;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.yggdrasil.labs.common.exception.SystemException;

/** 验证旧子类返回普通响应体时仍继承原有 HTTP 状态。 */
class LegacyHandlerStatusMvcIntegrationTest {

    @ParameterizedTest
    @CsvSource({"system,500", "parameter,400", "unknown,500"})
    void preservesStatusForLegacyOverrides(String failure, int expectedStatus) throws Exception {
        MockMvcBuilders.standaloneSetup(new FailureController())
                .setControllerAdvice(new LegacyHandler())
                .build()
                .perform(get("/legacy/" + failure))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value("consumer"));
    }

    @RestController
    static class FailureController {
        @GetMapping("/legacy/{failure}")
        Object fail(@PathVariable("failure") String failure) throws Exception {
            if ("system".equals(failure)) throw new SystemException("S", "系统异常");
            if ("parameter".equals(failure)) {
                throw new MissingServletRequestParameterException("name", "String");
            }
            throw new IllegalStateException("未知异常");
        }
    }

    @RestControllerAdvice
    static class LegacyHandler extends MimirExceptionHandler {
        LegacyHandler() {
            super(new DefaultExceptionResponseFactory());
        }

        @Override
        public Object handleSystemException(SystemException e, HttpServletRequest request) {
            return Map.of("code", "consumer");
        }

        @Override
        public Object handleMissingServletRequestParameterException(
                MissingServletRequestParameterException e, HttpServletRequest request) {
            return Map.of("code", "consumer");
        }

        @Override
        public Object handleException(Exception e, HttpServletRequest request) {
            return Map.of("code", "consumer");
        }
    }
}
