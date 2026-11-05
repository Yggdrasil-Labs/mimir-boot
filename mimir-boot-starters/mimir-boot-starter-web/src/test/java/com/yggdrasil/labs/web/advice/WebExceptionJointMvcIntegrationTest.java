package com.yggdrasil.labs.web.advice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yggdrasil.labs.common.constant.CommonConstants;
import com.yggdrasil.labs.common.constant.HttpHeaderConstants;
import com.yggdrasil.labs.common.exception.BizException;
import com.yggdrasil.labs.common.exception.ErrorCode;
import com.yggdrasil.labs.common.exception.ErrorDefinition;
import com.yggdrasil.labs.common.exception.SystemException;
import com.yggdrasil.labs.common.response.R;
import com.yggdrasil.labs.exception.handler.DefaultExceptionResponseFactory;
import com.yggdrasil.labs.exception.handler.ExceptionResponseFactory;
import com.yggdrasil.labs.exception.handler.MimirExceptionHandler;
import com.yggdrasil.labs.web.config.WebProperties;

/**
 * W4/E6 联合验收：使用真实 Boot 自动配置、Exception 处理器及 Web 增强器。
 *
 * <p>既有八个 ResponseEntity 分支由 {@link ExistingExceptionTraceMvcIntegrationTest} 和 {@link
 * ExistingMultipartTraceMvcIntegrationTest} 验证；本组补齐统一封装、工厂和下游 Advice 对照。
 */
@SpringBootTest(
        classes = WebExceptionJointMvcIntegrationTest.TestApplication.class,
        properties = "logging.level.root=OFF")
@AutoConfigureMockMvc
@Execution(ExecutionMode.SAME_THREAD)
class WebExceptionJointMvcIntegrationTest {

    private static final String TRACE_ID = "joint-mdc-trace";
    private static final String TRANSPORT_TRACE_ID = "joint-transport-trace";
    private static final String TRACE_HEADER = "X-Joint-Mdc-Trace";
    private static final String REQUEST_HEADER = "X-Joint-Mdc-Request";

    @Autowired private MockMvc mockMvc;
    @Autowired private ApplicationContext applicationContext;
    @Autowired private RecordingFactory factory;
    @Autowired private RuntimeBodyAdvice runtimeAdvice;
    @Autowired private DeclaredRBodyAdvice declaredAdvice;
    private Map<String, String> previousMdc;

    @BeforeEach
    void prepareRequestState() {
        previousMdc = MDC.getCopyOfContextMap();
        MDC.remove(CommonConstants.TRACE_ID);
        MDC.remove(CommonConstants.REQUEST_ID);
        factory.reset();
        runtimeAdvice.reset();
        declaredAdvice.reset();
        assertThat(applicationContext.getBean(MimirExceptionHandler.class))
                .isExactlyInstanceOf(MimirExceptionHandler.class);
        assertThat(applicationContext.getBean(ResponseBodyEnhancer.class))
                .isExactlyInstanceOf(ResponseBodyEnhancer.class);
    }

    @AfterEach
    void restoreRequestState() {
        if (previousMdc == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(previousMdc);
        }
    }

    @ParameterizedTest
    @CsvSource({"biz, 200, JOINT-BIZ", "system, 500, JOINT-SYSTEM", "ordinary, 500, 10000"})
    void unifiedExceptionResponsesKeepStatusAndReceiveTrace(
            String kind, int expectedStatus, String code) throws Exception {
        performFailure(kind, expectedStatus, code)
                .andExpect(jsonPath("$.traceId").value(TRACE_ID))
                .andExpect(
                        header().string(HttpHeaderConstants.TRACE_ID_HEADER, TRANSPORT_TRACE_ID));

        assertUnifiedResponseMetadata();
    }

    @Test
    void factoryEntityCannotOverrideResolvedStatusOrExceptionHeaders() throws Exception {
        factory.mode = FactoryMode.ENTITY;

        performFailure("limited", 429, ErrorCode.PARAM_ERROR.getCode())
                .andExpect(jsonPath("$.traceId").value(TRACE_ID))
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(
                        header().stringValues(
                                        "X-Joint-Collision", "exception-first", "exception-second"))
                .andExpect(header().string("X-Joint-Factory", "factory"))
                .andExpect(
                        header().string(HttpHeaderConstants.TRACE_ID_HEADER, TRANSPORT_TRACE_ID));

        assertUnifiedResponseMetadata();
    }

    @ParameterizedTest
    @CsvSource({
        "biz, 200, JOINT-BIZ",
        "system, 500, JOINT-SYSTEM",
        "ordinary, 500, 10000",
        "limited, 429, 00002"
    })
    void failingFactoryFallsBackOnceAndKeepsResolvedState(
            String kind, int expectedStatus, String code) throws Exception {
        factory.mode = FactoryMode.FAIL;

        ResultActions result =
                performFailure(kind, expectedStatus, code)
                        .andExpect(jsonPath("$.traceId").value(TRACE_ID))
                        .andExpect(jsonPath("$.data").doesNotExist())
                        .andExpect(header().doesNotExist("X-Joint-Factory"));
        if (kind.equals("limited")) {
            result.andExpect(header().string("Retry-After", "30"))
                    .andExpect(
                            header().stringValues(
                                            "X-Joint-Collision",
                                            "exception-first",
                                            "exception-second"));
        }
        assertThat(result.andReturn().getResponse().getContentAsString())
                .doesNotContain("factory-private-failure");
        assertUnifiedResponseMetadata();
    }

    @Test
    void existingFactoryTraceIsPreserved() throws Exception {
        factory.mode = FactoryMode.EXISTING_TRACE;

        performFailure("biz", 200, "JOINT-BIZ")
                .andExpect(jsonPath("$.traceId").value("factory-existing-trace"));

        assertUnifiedResponseMetadata();
    }

    @ParameterizedTest
    @CsvSource({
        "joint-trace, joint-request, joint-trace",
        "'', joint-request, joint-request",
        "'', '', ''"
    })
    void mdcTraceTakesPriorityThenRequestIdWithoutGeneratingANewValue(
            String traceId, String requestId, String expectedTrace) throws Exception {
        ResultActions result =
                mockMvc.perform(
                                request("/joint-exception/biz")
                                        .header(TRACE_HEADER, traceId)
                                        .header(REQUEST_HEADER, requestId))
                        .andExpect(status().isOk());

        if (expectedTrace.isEmpty()) {
            result.andExpect(jsonPath("$.traceId").doesNotExist());
        } else {
            result.andExpect(jsonPath("$.traceId").value(expectedTrace));
        }
        assertUnifiedResponseMetadata();
    }

    @ParameterizedTest
    @EnumSource(
            value = FactoryMode.class,
            names = {"STRING", "NON_R", "NULL"})
    void nonRAndNullFactoryResultsPassThroughTheRealAdviceChain(FactoryMode mode) throws Exception {
        factory.mode = mode;

        ResultActions result =
                mockMvc.perform(tracedRequest("/joint-exception/limited"))
                        .andExpect(status().isTooManyRequests())
                        .andExpect(header().string("Retry-After", "30"));
        switch (mode) {
            case STRING -> result.andExpect(content().string("factory-text"));
            case NON_R ->
                    result.andExpect(jsonPath("$.payload").value("factory-object"))
                            .andExpect(jsonPath("$.traceId").doesNotExist());
            case NULL -> result.andExpect(content().string(""));
            default -> throw new AssertionError("非预期测试模式 " + mode);
        }
        assertThat(factory.calls).isEqualTo(1);
        assertThat(runtimeAdvice.rWrites).isZero();
        assertThat(declaredAdvice.writes).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"plain-r", "typed-entity"})
    void normalRAndTypedResponseEntityKeepTheirDeclaredTypeEnhancement(String kind)
            throws Exception {
        mockMvc.perform(tracedRequest("/joint-success/" + kind))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("payload"))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID));

        assertThat(factory.calls).isZero();
        assertThat(runtimeAdvice.rWrites).isEqualTo(1);
        assertThat(declaredAdvice.writes).isEqualTo(1);
        assertThat(runtimeAdvice.supportType.getContainingClass()).isEqualTo(JointController.class);
        assertThat(runtimeAdvice.bodyType).isSameAs(runtimeAdvice.supportType);
    }

    @Test
    void normalNonRResponseIsNotConvertedIntoR() throws Exception {
        mockMvc.perform(tracedRequest("/joint-success/non-r"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload").value("normal-object"))
                .andExpect(jsonPath("$.traceId").doesNotExist());

        assertThat(factory.calls).isZero();
        assertThat(runtimeAdvice.rWrites).isZero();
        assertThat(declaredAdvice.writes).isZero();
    }

    @Test
    void downstreamAdviceSeesOriginalObjectDeclarationAndUnwrappedRBody() throws Exception {
        performFailure("biz", 200, "JOINT-BIZ");

        assertUnifiedResponseMetadata();
        assertThat(runtimeAdvice.body).isSameAs(factory.lastBody);
        assertThat(runtimeAdvice.body).isInstanceOf(R.class);
        // 仅依据声明的 R/ResponseEntity<R> 接入的 Advice 不会获得异常 Object 返回候选。
        assertThat(declaredAdvice.writes).isZero();
        assertThat(declaredAdvice.supportType).isSameAs(runtimeAdvice.supportType);
        assertThat(runtimeAdvice.bodyType).isSameAs(runtimeAdvice.supportType);
        assertThat(runtimeAdvice.bodyConverter).isSameAs(runtimeAdvice.supportConverter);
    }

    @Test
    void nullDefaultMessageIsOmittedWithoutLegacyFailByDefault() throws Exception {
        performFailure("null-message", 200, "JOINT-NULL")
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.fail").doesNotExist())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID));

        assertThat(factory.lastMessage).isNull();
        assertUnifiedResponseMetadata();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "mimir.boot.web.enabled=false",
                "mimir.boot.web.response.enabled=false",
                "mimir.boot.web.response.auto-fill-trace-id=false"
            })
    void eachEnhancementSwitchDisablesTraceOnRealExceptionResponses(String property) {
        contextRunner()
                .withPropertyValues(property)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            MockMvc mvc =
                                    MockMvcBuilders.webAppContextSetup(
                                                    context.getSourceApplicationContext())
                                            .build();
                            mvc.perform(tracedRequest("/joint-exception/biz"))
                                    .andExpect(status().isOk())
                                    .andExpect(jsonPath("$.code").value("JOINT-BIZ"))
                                    .andExpect(jsonPath("$.traceId").doesNotExist());
                            assertThat(context.getBean(RecordingFactory.class).calls).isEqualTo(1);
                            assertThat(context.getBean(MimirExceptionHandler.class))
                                    .isExactlyInstanceOf(MimirExceptionHandler.class);
                            if (property.equals("mimir.boot.web.enabled=false")) {
                                assertThat(context).doesNotHaveBean(ResponseBodyEnhancer.class);
                            }
                        });
    }

    @Test
    void writeNullsIncludesTheNullMessageWithoutChangingTheFactoryInput() {
        contextRunner()
                .withPropertyValues("mimir.boot.web.serialization.write-nulls=true")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            MockMvc mvc =
                                    MockMvcBuilders.webAppContextSetup(
                                                    context.getSourceApplicationContext())
                                            .build();
                            String body =
                                    mvc.perform(tracedRequest("/joint-exception/null-message"))
                                            .andExpect(status().isOk())
                                            .andExpect(jsonPath("$.traceId").value(TRACE_ID))
                                            .andReturn()
                                            .getResponse()
                                            .getContentAsString();
                            JsonNode json = context.getBean(ObjectMapper.class).readTree(body);
                            assertThat(json.has("message")).isTrue();
                            assertThat(json.get("message").isNull()).isTrue();
                            assertThat(json.has("data")).isTrue();
                            assertThat(json.get("data").isNull()).isTrue();
                            assertThat(json.has("fail")).isFalse();
                            RecordingFactory activeFactory =
                                    context.getBean(RecordingFactory.class);
                            assertThat(activeFactory.calls).isEqualTo(1);
                            assertThat(activeFactory.lastMessage).isNull();
                        });
    }

    @Test
    void consumerEnhancerMayAllowOrRejectTheUnifiedExceptionCandidate() {
        contextRunner()
                .withBean(CountingEnhancer.class)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            CountingEnhancer enhancer = context.getBean(CountingEnhancer.class);
                            RecordingFactory activeFactory =
                                    context.getBean(RecordingFactory.class);
                            RuntimeBodyAdvice observer = context.getBean(RuntimeBodyAdvice.class);
                            MockMvc mvc =
                                    MockMvcBuilders.webAppContextSetup(
                                                    context.getSourceApplicationContext())
                                            .build();
                            for (boolean allow : new boolean[] {true, false}) {
                                enhancer.reset(allow);
                                activeFactory.reset();
                                observer.reset();
                                ResultActions result =
                                        mvc.perform(tracedRequest("/joint-exception/biz"))
                                                .andExpect(status().isOk());
                                if (allow) {
                                    result.andExpect(jsonPath("$.traceId").value(TRACE_ID));
                                    assertThat(enhancer.writes).isEqualTo(1);
                                    assertThat(enhancer.bodyType).isSameAs(observer.bodyType);
                                    assertThat(enhancer.bodyConverter)
                                            .isSameAs(observer.bodyConverter);
                                } else {
                                    result.andExpect(jsonPath("$.traceId").doesNotExist());
                                    assertThat(enhancer.writes).isZero();
                                }
                                assertThat(enhancer.supportType).isSameAs(observer.supportType);
                                assertThat(activeFactory.calls).isEqualTo(1);
                            }
                            // 即便业务 supports 放行，内部 Object 包装器也不得调用非 R/null 的泛型桥接方法。
                            for (FactoryMode mode :
                                    new FactoryMode[] {
                                        FactoryMode.STRING, FactoryMode.NON_R, FactoryMode.NULL
                                    }) {
                                enhancer.reset(true);
                                activeFactory.reset();
                                activeFactory.mode = mode;
                                mvc.perform(tracedRequest("/joint-exception/biz"))
                                        .andExpect(status().isOk());
                                assertThat(enhancer.writes).isZero();
                                assertThat(activeFactory.calls).isEqualTo(1);
                            }
                        });
    }

    private ResultActions performFailure(String kind, int expectedStatus, String code)
            throws Exception {
        return mockMvc.perform(tracedRequest("/joint-exception/" + kind))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(code));
    }

    private void assertUnifiedResponseMetadata() {
        assertThat(factory.calls).isEqualTo(1);
        assertThat(runtimeAdvice.rWrites).isEqualTo(1);
        assertThat(runtimeAdvice.supportType.getContainingClass())
                .isEqualTo(MimirExceptionHandler.class);
        assertThat(runtimeAdvice.supportType.getParameterType()).isEqualTo(ResponseEntity.class);
        assertThat(runtimeAdvice.supportType.getGenericParameterType()).isEqualTo(Object.class);
        assertThat(runtimeAdvice.supportType.getMethod()).isNotNull();
        assertThat(runtimeAdvice.supportType.getMethod().getReturnType()).isEqualTo(Object.class);
    }

    private static WebApplicationContextRunner contextRunner() {
        return new WebApplicationContextRunner().withUserConfiguration(TestApplication.class);
    }

    private static MockHttpServletRequestBuilder tracedRequest(String path) {
        return request(path).header(TRACE_HEADER, TRACE_ID).header(REQUEST_HEADER, "joint-request");
    }

    private static MockHttpServletRequestBuilder request(String path) {
        return get(path)
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaderConstants.TRACE_ID_HEADER, TRANSPORT_TRACE_ID);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({
        JointController.class,
        RecordingFactory.class,
        RuntimeBodyAdvice.class,
        DeclaredRBodyAdvice.class
    })
    static class TestApplication {}

    @RestController
    static class JointController {

        @GetMapping("/joint-exception/{kind}")
        R<String> failure(
                @PathVariable("kind") String kind,
                @RequestHeader(value = TRACE_HEADER, required = false) String traceId,
                @RequestHeader(value = REQUEST_HEADER, required = false) String requestId) {
            setMdc(traceId, requestId);
            switch (kind) {
                case "biz" -> throw BizException.fromError(new JointError("JOINT-BIZ", "业务失败"));
                case "system" ->
                        throw SystemException.fromError(new JointError("JOINT-SYSTEM", "系统失败"));
                case "null-message" ->
                        throw BizException.fromError(new JointError("JOINT-NULL", null));
                case "limited" -> {
                    ErrorResponseException exception =
                            new ErrorResponseException(HttpStatus.TOO_MANY_REQUESTS);
                    exception.getHeaders().set("Retry-After", "30");
                    exception.getHeaders().add("X-Joint-Collision", "exception-first");
                    exception.getHeaders().add("X-Joint-Collision", "exception-second");
                    throw exception;
                }
                default -> throw new IllegalStateException("ordinary-private-failure");
            }
        }

        @GetMapping("/joint-success/plain-r")
        R<String> plain(
                @RequestHeader(value = TRACE_HEADER, required = false) String traceId,
                @RequestHeader(value = REQUEST_HEADER, required = false) String requestId) {
            setMdc(traceId, requestId);
            return R.success("payload");
        }

        @GetMapping("/joint-success/typed-entity")
        ResponseEntity<R<String>> typedEntity(
                @RequestHeader(value = TRACE_HEADER, required = false) String traceId,
                @RequestHeader(value = REQUEST_HEADER, required = false) String requestId) {
            return ResponseEntity.ok(plain(traceId, requestId));
        }

        @GetMapping("/joint-success/non-r")
        Map<String, String> nonR() {
            return Map.of("payload", "normal-object");
        }

        private void setMdc(String traceId, String requestId) {
            // 在真实请求内控制上下文，避免 TraceInterceptor 生成的值掩盖增强器的回退规则。
            MDC.remove(CommonConstants.TRACE_ID);
            MDC.remove(CommonConstants.REQUEST_ID);
            if (traceId != null && !traceId.isEmpty()) {
                MDC.put(CommonConstants.TRACE_ID, traceId);
            }
            if (requestId != null && !requestId.isEmpty()) {
                MDC.put(CommonConstants.REQUEST_ID, requestId);
            }
        }
    }

    private record JointError(String code, String message) implements ErrorDefinition {
        @Override
        public String getCode() {
            return code;
        }

        @Override
        public String getMessage() {
            return message;
        }
    }

    enum FactoryMode {
        DEFAULT,
        ENTITY,
        FAIL,
        EXISTING_TRACE,
        STRING,
        NON_R,
        NULL
    }

    static class RecordingFactory implements ExceptionResponseFactory {
        private final DefaultExceptionResponseFactory delegate =
                new DefaultExceptionResponseFactory();
        private FactoryMode mode = FactoryMode.DEFAULT;
        private int calls;
        private String lastMessage;
        private Object lastBody;

        @Override
        public Object createResponse(String code, String message, Object data) {
            calls++;
            lastMessage = message;
            lastBody =
                    switch (mode) {
                        case FAIL -> throw new IllegalStateException("factory-private-failure");
                        case STRING -> "factory-text";
                        case NON_R -> Map.of("code", code, "payload", "factory-object");
                        case NULL -> null;
                        default -> delegate.createResponse(code, message, data);
                    };
            if (mode == FactoryMode.EXISTING_TRACE) {
                ((R<?>) lastBody).setTraceId("factory-existing-trace");
            }
            if (mode == FactoryMode.ENTITY) {
                return ResponseEntity.accepted()
                        .header("Retry-After", "1")
                        .header("X-Joint-Collision", "factory")
                        .header("X-Joint-Factory", "factory")
                        .body(lastBody);
            }
            return lastBody;
        }

        private void reset() {
            mode = FactoryMode.DEFAULT;
            calls = 0;
            lastMessage = null;
            lastBody = null;
        }
    }

    /** 业务 Advice 广泛接入，在 beforeBodyWrite 中依据实际 body 决定是否处理 R。 */
    @RestControllerAdvice
    @Order(Ordered.LOWEST_PRECEDENCE)
    static class RuntimeBodyAdvice implements ResponseBodyAdvice<Object> {
        private MethodParameter supportType;
        private MethodParameter bodyType;
        private Class<? extends HttpMessageConverter<?>> supportConverter;
        private Class<? extends HttpMessageConverter<?>> bodyConverter;
        private Object body;
        private int rWrites;

        @Override
        public boolean supports(
                MethodParameter returnType,
                Class<? extends HttpMessageConverter<?>> converterType) {
            supportType = returnType;
            supportConverter = converterType;
            return true;
        }

        @Override
        public Object beforeBodyWrite(
                Object body,
                MethodParameter returnType,
                MediaType selectedContentType,
                Class<? extends HttpMessageConverter<?>> selectedConverterType,
                ServerHttpRequest request,
                ServerHttpResponse response) {
            this.body = body;
            bodyType = returnType;
            bodyConverter = selectedConverterType;
            if (body instanceof R<?>) {
                rWrites++;
            }
            return body;
        }

        private void reset() {
            supportType = null;
            bodyType = null;
            supportConverter = null;
            bodyConverter = null;
            body = null;
            rWrites = 0;
        }
    }

    /** 对照组仅从声明类型寻找 R，不借助框架内部的 Object 候选兼容。 */
    @RestControllerAdvice
    @Order(Ordered.LOWEST_PRECEDENCE - 1)
    static class DeclaredRBodyAdvice implements ResponseBodyAdvice<Object> {
        private MethodParameter supportType;
        private int writes;

        @Override
        public boolean supports(
                MethodParameter returnType,
                Class<? extends HttpMessageConverter<?>> converterType) {
            supportType = returnType;
            Class<?> rawType = returnType.getParameterType();
            if (R.class.isAssignableFrom(rawType)) {
                return true;
            }
            Class<?> bodyType =
                    ResolvableType.forMethodParameter(returnType).getGeneric(0).resolve();
            return HttpEntity.class.isAssignableFrom(rawType)
                    && bodyType != null
                    && R.class.isAssignableFrom(bodyType);
        }

        @Override
        public Object beforeBodyWrite(
                Object body,
                MethodParameter returnType,
                MediaType selectedContentType,
                Class<? extends HttpMessageConverter<?>> selectedConverterType,
                ServerHttpRequest request,
                ServerHttpResponse response) {
            writes++;
            return body;
        }

        private void reset() {
            supportType = null;
            writes = 0;
        }
    }

    static class CountingEnhancer extends ResponseBodyEnhancer {
        private boolean allow;
        private int writes;
        private MethodParameter supportType;
        private MethodParameter bodyType;
        private Class<? extends HttpMessageConverter<?>> bodyConverter;

        CountingEnhancer(WebProperties properties) {
            super(properties);
        }

        @Override
        public boolean supports(
                MethodParameter returnType,
                Class<? extends HttpMessageConverter<?>> converterType) {
            supportType = returnType;
            return allow && super.supports(returnType, converterType);
        }

        @Override
        public R<?> beforeBodyWrite(
                R<?> body,
                MethodParameter returnType,
                MediaType selectedContentType,
                Class<? extends HttpMessageConverter<?>> selectedConverterType,
                ServerHttpRequest request,
                ServerHttpResponse response) {
            writes++;
            bodyType = returnType;
            bodyConverter = selectedConverterType;
            return super.beforeBodyWrite(
                    body,
                    returnType,
                    selectedContentType,
                    selectedConverterType,
                    request,
                    response);
        }

        private void reset(boolean allow) {
            this.allow = allow;
            writes = 0;
            supportType = null;
            bodyType = null;
            bodyConverter = null;
        }
    }
}
