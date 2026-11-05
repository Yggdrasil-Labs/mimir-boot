package com.yggdrasil.labs.exception.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import com.yggdrasil.labs.common.exception.BaseException;
import com.yggdrasil.labs.common.exception.BizException;
import com.yggdrasil.labs.common.exception.ErrorCode;
import com.yggdrasil.labs.common.exception.IException;
import com.yggdrasil.labs.common.exception.SystemException;
import com.yggdrasil.labs.common.response.R;

/** 通过原生 MVC resolver 和转换器链验证异常 HTTP 契约。 */
class HttpExceptionMvcIntegrationTest {

    private MockMvc mvc(ExceptionResponseFactory factory) {
        return MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new MimirExceptionHandler(factory))
                .build();
    }

    private MockMvc mvc(Exception exception, ExceptionResponseFactory factory) {
        return MockMvcBuilders.standaloneSetup(new ExceptionController(exception))
                .setControllerAdvice(new MimirExceptionHandler(factory))
                .build();
    }

    @Test
    void preservesAllowForMethodNotAllowed() throws Exception {
        mvc(new DefaultExceptionResponseFactory())
                .perform(post("/ok"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "GET"));
    }

    @Test
    void preservesExplicitStatusAndRetryAfterWithoutExposingReason() throws Exception {
        mvc(new DefaultExceptionResponseFactory())
                .perform(get("/limited"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.code").value("00002"))
                .andExpect(jsonPath("$.message").value("参数错误"));
    }

    @Test
    void resolvesStatusAnnotationOnCurrentExceptionType() throws Exception {
        mvc(new DefaultExceptionResponseFactory())
                .perform(get("/annotated"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("20001"));
    }

    @ParameterizedTest
    @CsvSource({
        "400,00002",
        "401,30000",
        "403,30001",
        "404,20001",
        "405,20004",
        "406,20004",
        "413,00004",
        "415,20004",
        "429,00002",
        "499,00002",
        "503,10000",
        "599,10000",
        "201,00001",
        "302,00001"
    })
    void mapsExplicitStatusWithoutReadingReasonOrProblemDetail(int statusCode, String code)
            throws Exception {
        ErrorResponseException exception =
                new ErrorResponseException(HttpStatusCode.valueOf(statusCode));
        exception.setDetail("secret ProblemDetail");
        exception.getHeaders().set("Retry-After", "60");

        mvc(exception, new DefaultExceptionResponseFactory())
                .perform(get("/failure"))
                .andExpect(status().is(statusCode))
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        com.yggdrasil.labs.common.exception.ErrorCode.fromCode(code)
                                                .getMessage()))
                .andExpect(jsonPath("$.detail").doesNotExist());
    }

    @Test
    void supportsInheritedAndComposedStatusButDoesNotTraverseCause() throws Exception {
        for (Exception exception :
                List.of(new InheritedMissingException(), new ComposedException())) {
            mvc(exception, new DefaultExceptionResponseFactory())
                    .perform(get("/failure"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("数据不存在"));
        }
        mvc(new RuntimeException(new MissingException()), new DefaultExceptionResponseFactory())
                .perform(get("/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("10000"));
    }

    @Test
    void retainsBusinessCodeForAnnotatedIException() throws Exception {
        mvc(new AnnotatedBusinessException(), new DefaultExceptionResponseFactory())
                .perform(get("/failure"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUSTOM"))
                .andExpect(jsonPath("$.message").value("业务文案"));
    }

    @Test
    void doesNotAdaptEveryErrorResponseImplementation() throws Exception {
        mvc(new UnmappedErrorResponse(), new DefaultExceptionResponseFactory())
                .perform(get("/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("10000"));
    }

    @Test
    void fixedFrameworkStatusWinsOverSubclassAnnotationsAndFactoryStatus() throws Exception {
        List<Exception> exceptions =
                List.of(
                        new AnnotatedBizException(), new AnnotatedSystemException(),
                        new AnnotatedBaseException(), new BizException("B", "业务"),
                        new SystemException("S", "系统"), new BaseException("BASE", "基础") {});
        for (Exception exception : exceptions) {
            int expected = exception instanceof BizException ? 200 : 500;
            mvc(exception, (code, message, data) -> ResponseEntity.accepted().body("factory"))
                    .perform(get("/failure"))
                    .andExpect(status().is(expected))
                    .andExpect(content().string("factory"));
        }
    }

    @Test
    void commonErrorFactoriesRetainFrameworkStatusCodeAndMessage() throws Exception {
        mvc(
                        BizException.fromError(ErrorCode.DATA_NOT_FOUND, "业务文案"),
                        new DefaultExceptionResponseFactory())
                .perform(get("/failure"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("20001"))
                .andExpect(jsonPath("$.message").value("业务文案"));
        mvc(
                        SystemException.fromError(ErrorCode.SYSTEM_ERROR, "系统文案"),
                        new DefaultExceptionResponseFactory())
                .perform(get("/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("10000"))
                .andExpect(jsonPath("$.message").value("系统文案"));
    }

    @Test
    void duplicateSubclassMappingFailsAndOverrideRemainsUsable() throws Exception {
        assertThrows(
                IllegalStateException.class,
                () ->
                        MockMvcBuilders.standaloneSetup(new ThrowingController())
                                .setControllerAdvice(new DuplicateStatusAdvice())
                                .build());
        MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new OverrideStatusAdvice())
                .build()
                .perform(get("/limited"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void mergesLegalMultiValueHeadersWithExceptionPriorityAndPreservesFilterHeaders()
            throws Exception {
        StatusException exception = new StatusException(HttpStatus.TOO_MANY_REQUESTS);
        exception.getHeaders().add("X-Multiple", "exception-one");
        exception.getHeaders().add("X-Multiple", "exception-two");
        exception.getHeaders().set("Retry-After", "20");
        exception.getHeaders().set("Content-Type", "application/problem+json");
        for (String name : List.of("Accept", "Accept-Patch", "Location", "WWW-Authenticate")) {
            exception.getHeaders().set(name, "protocol-value");
        }
        HttpHeaders factoryHeaders = new HttpHeaders();
        factoryHeaders.add("x-multiple", "factory-value");
        factoryHeaders.add("Set-Cookie", "a=1");
        factoryHeaders.add("Set-Cookie", "b=2");
        factoryHeaders.set("X-Factory", "kept");
        Filter filter =
                (request, response, chain) -> {
                    ((jakarta.servlet.http.HttpServletResponse) response)
                            .setHeader("X-Filter", "kept");
                    chain.doFilter(request, response);
                };
        MockMvc mvc =
                MockMvcBuilders.standaloneSetup(new ExceptionController(exception))
                        .addFilters(filter)
                        .setControllerAdvice(
                                new MimirExceptionHandler(
                                        (code, message, data) ->
                                                new ResponseEntity<>(
                                                        "factory",
                                                        factoryHeaders,
                                                        HttpStatus.ACCEPTED)))
                        .build();

        mvc.perform(get("/failure"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().stringValues("X-Multiple", "exception-one", "exception-two"))
                .andExpect(header().stringValues("Set-Cookie", "a=1", "b=2"))
                .andExpect(header().string("Retry-After", "20"))
                .andExpect(header().string("X-Factory", "kept"))
                .andExpect(header().string("X-Filter", "kept"))
                .andExpect(header().string("Accept", "protocol-value"))
                .andExpect(header().string("Accept-Patch", "protocol-value"))
                .andExpect(header().string("Location", "protocol-value"))
                .andExpect(header().string("WWW-Authenticate", "protocol-value"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN));
        assertEquals(List.of("factory-value"), factoryHeaders.get("x-multiple"));
        assertEquals(
                List.of("exception-one", "exception-two"),
                exception.getHeaders().get("X-Multiple"));
    }

    @Test
    void filtersHopHeadersInvalidNamesAndValuesAndEntityMetadataFromBothSources() throws Exception {
        StatusException exception = new StatusException(HttpStatus.TOO_MANY_REQUESTS);
        HttpHeaders factoryHeaders = new HttpHeaders();
        for (HttpHeaders headers : List.of(exception.getHeaders(), factoryHeaders)) {
            headers.set("Connection", "X-Dynamic, x-other");
            for (String name :
                    List.of(
                            "Keep-Alive",
                            "Proxy-Connection",
                            "TE",
                            "Trailer",
                            "Transfer-Encoding",
                            "Upgrade",
                            "Proxy-Authenticate",
                            "Proxy-Authorization",
                            "X-Dynamic",
                            "X-Other",
                            "Content-Encoding")) {
                headers.set(name, "discarded");
            }
            headers.set("Content-Length", "999");
            headers.set("Bad Header", "value");
            headers.set("Bad\rName", "value");
            headers.add("X-Values", "bad\rvalue");
            headers.add("X-Values", "bad\nvalue");
            headers.add("X-Values", "bad\0value");
            headers.add("X-Values", "good");
        }
        var response =
                mvc(
                                exception,
                                (code, message, data) ->
                                        new ResponseEntity<>("body", factoryHeaders, HttpStatus.OK))
                        .perform(get("/failure"))
                        .andExpect(status().isTooManyRequests())
                        .andExpect(header().stringValues("X-Values", "good"))
                        .andExpect(content().string("body"))
                        .andReturn()
                        .getResponse();
        for (String name :
                List.of(
                        "Connection",
                        "Keep-Alive",
                        "Proxy-Connection",
                        "TE",
                        "Trailer",
                        "Transfer-Encoding",
                        "Upgrade",
                        "Proxy-Authenticate",
                        "Proxy-Authorization",
                        "X-Dynamic",
                        "X-Other",
                        "Content-Encoding",
                        "Bad Header",
                        "Bad\rName")) {
            assertFalse(response.containsHeader(name), name);
        }
        assertNotEquals("999", response.getHeader("Content-Length"));
    }

    @Test
    void snapshotsExceptionHeadersBeforeFactoryAndCallsFailingFactoryOnce() throws Exception {
        StatusException exception = new StatusException(HttpStatus.TOO_MANY_REQUESTS);
        exception.getHeaders().set("Retry-After", "30");
        AtomicInteger calls = new AtomicInteger();
        mvc(
                        exception,
                        (code, message, data) -> {
                            calls.incrementAndGet();
                            exception.getHeaders().set("Retry-After", "changed");
                            throw new IllegalStateException("factory secret");
                        })
                .perform(get("/failure"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.code").value("00002"))
                .andExpect(jsonPath("$.message").value("参数错误"))
                .andExpect(jsonPath("$.data").doesNotExist());
        assertEquals(1, calls.get());
    }

    @Test
    void nullFactoryBodyIsIntentionalAndDoesNotTriggerFallback() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        mvc(
                        new StatusException(HttpStatus.TOO_MANY_REQUESTS),
                        (code, message, data) -> {
                            calls.incrementAndGet();
                            return null;
                        })
                .perform(get("/failure"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().string(""));
        assertEquals(1, calls.get());
    }

    @ParameterizedTest
    @ValueSource(ints = {100, 103, 204, 205, 304})
    void bodyForbiddenStatusSkipsFactoryAndRetainsLegalHeaders(int statusCode) throws Exception {
        ErrorResponseException exception =
                new ErrorResponseException(HttpStatusCode.valueOf(statusCode));
        exception.getHeaders().set("X-Protocol", "kept");
        AtomicInteger calls = new AtomicInteger();
        MimirExceptionHandler handler =
                new MimirExceptionHandler(
                        (code, message, data) -> {
                            calls.incrementAndGet();
                            return "unexpected";
                        });
        ResponseEntity<?> entity =
                (ResponseEntity<?>)
                        handler.handleErrorResponseException(
                                exception,
                                new org.springframework.mock.web.MockHttpServletRequest());
        assertNull(entity.getBody());
        assertEquals(statusCode, entity.getStatusCode().value());
        assertEquals("kept", entity.getHeaders().getFirst("X-Protocol"));
        MockMvcBuilders.standaloneSetup(new ExceptionController(exception))
                .setControllerAdvice(handler)
                .build()
                .perform(get("/failure"))
                .andExpect(status().is(statusCode))
                .andExpect(header().string("X-Protocol", "kept"))
                .andExpect(content().string(""));
        assertEquals(0, calls.get());
    }

    @Test
    void headUsesTheSameNativeMvcProcessingAsAnOrdinaryEntity() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var response =
                mvc(
                                new StatusException(HttpStatus.TOO_MANY_REQUESTS),
                                (code, message, data) -> {
                                    calls.incrementAndGet();
                                    return "factory-body";
                                })
                        .perform(head("/failure"))
                        .andExpect(status().isTooManyRequests())
                        .andReturn()
                        .getResponse();
        var nativeResponse =
                MockMvcBuilders.standaloneSetup(new NativeEntityController())
                        .build()
                        .perform(head("/native-entity"))
                        .andReturn()
                        .getResponse();
        assertEquals(nativeResponse.getContentAsString(), response.getContentAsString());
        assertEquals(
                nativeResponse.getHeader("Content-Length"), response.getHeader("Content-Length"));
        assertEquals(1, calls.get());
    }

    @Test
    void consumerConverterAndExplicitFactoryContentTypeRemainAvailable() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        MediaType type = MediaType.parseMediaType("application/vnd.consumer");
        MockMvcBuilders.standaloneSetup(
                        new ExceptionController(new StatusException(HttpStatus.TOO_MANY_REQUESTS)))
                .setMessageConverters(new PayloadConverter(calls, false))
                .setControllerAdvice(
                        new MimirExceptionHandler(
                                (code, message, data) ->
                                        ResponseEntity.ok().contentType(type).body(new Payload())))
                .build()
                .perform(get("/failure").accept(MediaType.IMAGE_PNG))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentType(type))
                .andExpect(content().string("consumer"));
        assertEquals(1, calls.get());
    }

    @Test
    void conversionFailureIsNotRetriedAsFactoryFailure() throws Exception {
        AtomicInteger factories = new AtomicInteger();
        AtomicInteger writes = new AtomicInteger();
        MockMvc mvc =
                MockMvcBuilders.standaloneSetup(
                                new ExceptionController(
                                        new StatusException(HttpStatus.TOO_MANY_REQUESTS)))
                        .setMessageConverters(new PayloadConverter(writes, true))
                        .setControllerAdvice(
                                new MimirExceptionHandler(
                                        (code, message, data) -> {
                                            factories.incrementAndGet();
                                            return new Payload();
                                        }))
                        .build();
        var response =
                mvc.perform(get("/failure").accept("application/vnd.consumer"))
                        .andReturn()
                        .getResponse();
        assertEquals(1, factories.get());
        assertEquals(1, writes.get());
        // 写出失败后由 Spring 原 resolver 继续处理原异常，未构建 R 降级体。
        assertEquals("secret reason", response.getErrorMessage());
    }

    @Test
    void unrepresentableMediaDoesNotReinvokeFactory() throws Exception {
        AtomicInteger factories = new AtomicInteger();
        mvc(
                        new StatusException(HttpStatus.TOO_MANY_REQUESTS),
                        (code, message, data) -> {
                            factories.incrementAndGet();
                            return R.fail(code, message);
                        })
                .perform(get("/failure").accept(MediaType.IMAGE_PNG));
        assertEquals(1, factories.get());
    }

    @Test
    void resourceRangeAndConditionalRequestsUseNativeMvcProcessing() throws Exception {
        ExceptionResponseFactory resourceFactory =
                (code, message, data) ->
                        ResponseEntity.ok()
                                .eTag("\"resource-v1\"")
                                .body(
                                        new ByteArrayResource(
                                                "abcdef".getBytes(StandardCharsets.UTF_8)));
        MockMvc mvc = mvc(new BizException("B", "业务"), resourceFactory);
        mvc.perform(get("/failure").header("Range", "bytes=1-3"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 1-3/6"))
                .andExpect(content().string("bcd"));
        mvc.perform(get("/failure").header("If-None-Match", "\"resource-v1\""))
                .andExpect(status().isNotModified())
                .andExpect(content().string(""));
    }

    @Test
    void higherPriorityAdviceRetainsControl() throws Exception {
        MockMvcBuilders.standaloneSetup(new ExceptionController(new MissingException()))
                .setControllerAdvice(
                        new MimirExceptionHandler(new DefaultExceptionResponseFactory()),
                        new PriorityAdvice())
                .build()
                .perform(get("/failure"))
                .andExpect(status().isAccepted())
                .andExpect(content().string("priority"));
    }

    @Test
    void downstreamAdviceSeesOriginalMethodAndRuntimeEntityWithUnwrappedBody() throws Exception {
        RecordingAdvice advice = new RecordingAdvice();
        MockMvcBuilders.standaloneSetup(new ExceptionController(new BizException("B", "业务")))
                .setControllerAdvice(
                        new MimirExceptionHandler(new DefaultExceptionResponseFactory()), advice)
                .build()
                .perform(get("/failure"))
                .andExpect(status().isOk());
        assertEquals(1, advice.calls);
        assertEquals(ResponseEntity.class, advice.parameter.getParameterType());
        assertEquals(Object.class, advice.parameter.getMethod().getReturnType());
        assertEquals(MimirExceptionHandler.class, advice.parameter.getContainingClass());
        assertInstanceOf(R.class, advice.body);
    }

    @RestController
    private record ExceptionController(Exception exception) {
        @GetMapping("/failure")
        @org.springframework.web.bind.annotation.ResponseBody
        Object failure() throws Exception {
            throw exception;
        }
    }

    private static class Payload {}

    @RestControllerAdvice
    private static class DuplicateStatusAdvice extends MimirExceptionHandler {
        DuplicateStatusAdvice() {
            super(new DefaultExceptionResponseFactory());
        }

        @ExceptionHandler(ResponseStatusException.class)
        public Object duplicate(ResponseStatusException e) {
            return "duplicate";
        }
    }

    @RestControllerAdvice
    private static class OverrideStatusAdvice extends MimirExceptionHandler {
        OverrideStatusAdvice() {
            super(new DefaultExceptionResponseFactory());
        }

        @Override
        @ExceptionHandler(ResponseStatusException.class)
        public Object handleResponseStatusException(
                ResponseStatusException e, HttpServletRequest request) {
            return super.handleResponseStatusException(e, request);
        }
    }

    private static class UnmappedErrorResponse extends RuntimeException implements ErrorResponse {
        @Override
        public HttpStatusCode getStatusCode() {
            throw new AssertionError("未知 ErrorResponse 不应解析状态");
        }

        @Override
        public org.springframework.http.ProblemDetail getBody() {
            throw new AssertionError("不应读取原异常实体");
        }

        @Override
        public HttpHeaders getHeaders() {
            throw new AssertionError("未知 ErrorResponse 不应解析头");
        }
    }

    @RestController
    private static class NativeEntityController {
        @GetMapping("/native-entity")
        ResponseEntity<String> entity() {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("factory-body");
        }
    }

    private static class PayloadConverter extends AbstractHttpMessageConverter<Payload> {
        private final AtomicInteger writes;
        private final boolean fail;

        PayloadConverter(AtomicInteger writes, boolean fail) {
            super(MediaType.parseMediaType("application/vnd.consumer"));
            this.writes = writes;
            this.fail = fail;
        }

        @Override
        protected boolean supports(Class<?> type) {
            return type == Payload.class;
        }

        @Override
        protected Payload readInternal(Class<? extends Payload> type, HttpInputMessage message) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void writeInternal(Payload value, HttpOutputMessage message) throws IOException {
            writes.incrementAndGet();
            if (fail) {
                throw new HttpMessageNotWritableException("consumer failure");
            }
            message.getBody().write("consumer".getBytes(StandardCharsets.UTF_8));
        }
    }

    @RestControllerAdvice
    @Order(-1)
    private static class PriorityAdvice {
        @ExceptionHandler(MissingException.class)
        ResponseEntity<String> handle() {
            return ResponseEntity.accepted().body("priority");
        }
    }

    @RestControllerAdvice
    private static class RecordingAdvice implements ResponseBodyAdvice<Object> {
        private int calls;
        private MethodParameter parameter;
        private Object body;

        @Override
        public boolean supports(
                MethodParameter type, Class<? extends HttpMessageConverter<?>> converter) {
            parameter = type;
            return true;
        }

        @Override
        public Object beforeBodyWrite(
                Object value,
                MethodParameter type,
                MediaType media,
                Class<? extends HttpMessageConverter<?>> converter,
                ServerHttpRequest request,
                ServerHttpResponse response) {
            calls++;
            body = value;
            return value;
        }
    }

    @RestController
    private static final class ThrowingController {
        @GetMapping("/ok")
        String ok() {
            return "ok";
        }

        @GetMapping("/limited")
        String limited() {
            ResponseStatusException exception = new StatusException(HttpStatus.TOO_MANY_REQUESTS);
            exception.getHeaders().set("Retry-After", "30");
            throw exception;
        }

        @GetMapping("/annotated")
        String annotated() {
            throw new MissingException();
        }
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    private static class MissingException extends RuntimeException {}

    private static class InheritedMissingException extends MissingException {}

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    private @interface MissingStatus {}

    @MissingStatus
    private static class ComposedException extends RuntimeException {}

    @ResponseStatus(HttpStatus.NOT_FOUND)
    private static class AnnotatedBusinessException extends RuntimeException implements IException {
        @Override
        public String getCode() {
            return "CUSTOM";
        }

        @Override
        public String getMessage() {
            return "业务文案";
        }
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    private static class AnnotatedBizException extends BizException {
        AnnotatedBizException() {
            super("B", "业务");
        }
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    private static class AnnotatedSystemException extends SystemException {
        AnnotatedSystemException() {
            super("S", "系统");
        }
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    private static class AnnotatedBaseException extends BaseException {
        AnnotatedBaseException() {
            super("BASE", "基础");
        }
    }

    private static class StatusException extends ResponseStatusException {
        private final HttpHeaders headers = new HttpHeaders();

        StatusException(HttpStatus status) {
            super(status, "secret reason");
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }
    }
}
