package com.yggdrasil.labs.web.advice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.util.Map;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.yggdrasil.labs.common.constant.HttpHeaderConstants;
import com.yggdrasil.labs.common.exception.ErrorCode;
import com.yggdrasil.labs.common.exception.SystemException;
import com.yggdrasil.labs.common.response.R;

/**
 * W5：经 Web 与 Exception 自动配置的完整 MVC 链回传 X-Request-Id
 *
 * <p>是否带该头取决于失败发生的阶段：拦截器执行后的异常带头；Filter、更早拦截器、处理器匹配阶段的失败不带头。
 */
@SpringBootTest(
        classes = RequestIdHeaderMvcIntegrationTest.TestApplication.class,
        properties = "logging.level.root=OFF")
@AutoConfigureMockMvc
@Execution(ExecutionMode.SAME_THREAD)
class RequestIdHeaderMvcIntegrationTest {

    private static final String REQUEST_ID = "joint-request-1";
    private static final String BASE = "/request-id-mvc";

    @Autowired private MockMvc mockMvc;
    private Map<String, String> previousMdc;

    @BeforeEach
    void saveMdc() {
        previousMdc = MDC.getCopyOfContextMap();
    }

    @AfterEach
    void restoreMdc() {
        if (previousMdc == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(previousMdc);
        }
    }

    @Test
    void unifiedExceptionResponsesCarryRequestId() throws Exception {
        perform(get(BASE + "/system-failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ErrorCode.SYSTEM_ERROR.getCode()))
                .andExpect(header().string(HttpHeaderConstants.REQUEST_ID_HEADER, REQUEST_ID));
        // 参数解析在拦截器之后，缺少必填参数的 400 同样带头
        perform(get(BASE + "/required-param"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaderConstants.REQUEST_ID_HEADER, REQUEST_ID));
    }

    @Test
    void handlerMatchingFailuresDoNotCarryRequestId() throws Exception {
        assertNoRequestId(perform(post(BASE + "/ok")), 405);
        assertNoRequestId(
                perform(post(BASE + "/json").contentType(MediaType.TEXT_PLAIN).content("x")), 415);
        assertNoRequestId(perform(get(BASE + "/plain").accept(MediaType.APPLICATION_JSON)), 406);
        assertNoRequestId(perform(get(BASE + "/mode").param("mode", "other")), 400);
    }

    @Test
    void defaultStaticResourceNotFoundHappensAfterInterceptor() throws Exception {
        perform(get("/request-id-missing-resource.txt"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaderConstants.REQUEST_ID_HEADER, REQUEST_ID));
    }

    @Test
    void headerWrittenByEarlierFilterIsPreserved() throws Exception {
        MvcResult result =
                perform(get(BASE + "/ok").header(PresetHeaderFilter.TRIGGER, "true"))
                        .andExpect(status().isOk())
                        .andReturn();

        assertThat(result.getResponse().getHeaders(HttpHeaderConstants.REQUEST_ID_HEADER))
                .containsExactly("filter-1");
    }

    @Test
    void rejectionsBeforeTraceInterceptorDoNotCarryRequestId() throws Exception {
        assertNoRequestId(perform(get(BASE + "/ok").header(RejectingFilter.TRIGGER, "true")), 401);
        assertNoRequestId(perform(get(BASE + "/guarded")), 403);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaderConstants.REQUEST_ID_HEADER, REQUEST_ID));
    }

    private static void assertNoRequestId(ResultActions actions, int status) throws Exception {
        actions.andExpect(status().is(status))
                .andExpect(header().doesNotExist(HttpHeaderConstants.REQUEST_ID_HEADER));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(RequestIdController.class)
    static class TestApplication {

        @Bean
        PresetHeaderFilter presetHeaderFilter() {
            return new PresetHeaderFilter();
        }

        @Bean
        RejectingFilter rejectingFilter() {
            return new RejectingFilter();
        }

        @Bean
        WebMvcConfigurer guardingInterceptorConfigurer() {
            return new WebMvcConfigurer() {
                @Override
                public void addInterceptors(InterceptorRegistry registry) {
                    registry.addInterceptor(new GuardingInterceptor())
                            .addPathPatterns(BASE + "/guarded")
                            .order(-1000);
                }
            };
        }
    }

    @RestController
    static class RequestIdController {

        @GetMapping(BASE + "/ok")
        R<String> ok() {
            return R.success("ok");
        }

        @GetMapping(BASE + "/guarded")
        R<String> guarded() {
            return R.success("guarded");
        }

        @GetMapping(BASE + "/system-failure")
        R<String> systemFailure() {
            throw SystemException.fromError(ErrorCode.SYSTEM_ERROR);
        }

        @GetMapping(BASE + "/required-param")
        R<String> requiredParam(@RequestParam("value") String value) {
            return R.success(value);
        }

        @PostMapping(value = BASE + "/json", consumes = MediaType.APPLICATION_JSON_VALUE)
        R<String> json(@RequestBody Map<String, String> body) {
            return R.success(body.toString());
        }

        @GetMapping(value = BASE + "/plain", produces = MediaType.TEXT_PLAIN_VALUE)
        String plain() {
            return "plain";
        }

        @GetMapping(value = BASE + "/mode", params = "mode=expected")
        R<String> mode() {
            return R.success("mode");
        }
    }

    /** 模拟在拦截器之前自行写入 X-Request-Id 的业务 Filter */
    static class PresetHeaderFilter extends OncePerRequestFilter {
        static final String TRIGGER = "X-Test-Preset-Request-Id";

        @Override
        protected void doFilterInternal(
                HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            if (request.getHeader(TRIGGER) != null) {
                response.setHeader(HttpHeaderConstants.REQUEST_ID_HEADER, "filter-1");
            }
            chain.doFilter(request, response);
        }
    }

    /** 模拟安全框架在进入 DispatcherServlet 前拒绝请求 */
    static class RejectingFilter extends OncePerRequestFilter {
        static final String TRIGGER = "X-Test-Reject";

        @Override
        protected void doFilterInternal(
                HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            if (request.getHeader(TRIGGER) != null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            chain.doFilter(request, response);
        }
    }

    /** 排在 Trace 拦截器之前并拒绝请求的业务拦截器 */
    static class GuardingInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(
                HttpServletRequest request, HttpServletResponse response, Object handler) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
    }
}
