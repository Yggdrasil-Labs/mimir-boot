package com.yggdrasil.labs.exception.handler;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.yggdrasil.labs.common.exception.*;
import com.yggdrasil.labs.common.response.R;
import com.yggdrasil.labs.test.base.BaseUnitTest;
import com.yggdrasil.labs.test.util.AssertUtils;
import com.yggdrasil.labs.test.util.TestUtils;
import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.slf4j.LoggerFactory;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * MimirExceptionHandler 测试
 *
 * @author Yggdrasil Labs
 * @since 2.1.0
 */
class MimirExceptionHandlerTest extends BaseUnitTest {

    private MimirExceptionHandler handler;

    @Mock
    private HttpServletRequest request;

    @Override
    @BeforeEach
    public void setUp() {
        super.setUp();
        handler = new MimirExceptionHandler(new DefaultExceptionResponseFactory());
        when(request.getRequestURI()).thenReturn(TestUtils.randomUri("/test/api"));
    }

    @Test
    void testHandleBizException() {
        BizException exception = new BizException("20001", "用户不存在");

        Object response = handler.handleBizException(exception, request);

        assertInstanceOf(R.class, response);
        R<?> r = (R<?>) response;
        AssertUtils.assertEquals("20001", r.getCode());
        AssertUtils.assertEquals("用户不存在", r.getMessage());
        assertNull(r.getData());
    }

    @Test
    void testHandleBizExceptionWithErrorCode() {
        BizException exception = new BizException(ErrorCode.DATA_NOT_FOUND);

        Object response = handler.handleBizException(exception, request);

        assertInstanceOf(R.class, response);
        R<?> r = (R<?>) response;
        AssertUtils.assertEquals(ErrorCode.DATA_NOT_FOUND.getCode(), r.getCode());
        AssertUtils.assertEquals(ErrorCode.DATA_NOT_FOUND.getMessage(), r.getMessage());
    }

    @Test
    void testHandleSystemException() {
        SystemException exception = new SystemException("10000", "系统错误");

        Object response = handler.handleSystemException(exception, request);

        assertInstanceOf(R.class, response);
        R<?> r = (R<?>) response;
        AssertUtils.assertEquals("10000", r.getCode());
        AssertUtils.assertEquals("系统错误", r.getMessage());
        assertNull(r.getData());
    }

    @Test
    void testHandleSystemExceptionWithErrorCode() {
        SystemException exception = new SystemException(ErrorCode.SYSTEM_ERROR);

        Object response = handler.handleSystemException(exception, request);

        assertInstanceOf(R.class, response);
        R<?> r = (R<?>) response;
        AssertUtils.assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), r.getCode());
        AssertUtils.assertEquals(ErrorCode.SYSTEM_ERROR.getMessage(), r.getMessage());
    }

    @Test
    void testHandleBaseException() {
        BaseException exception = new BaseException("99999", "基础异常") {};

        Object response = handler.handleBaseException(exception, request);

        assertInstanceOf(R.class, response);
        R<?> r = (R<?>) response;
        AssertUtils.assertEquals("99999", r.getCode());
        AssertUtils.assertEquals("基础异常", r.getMessage());
    }

    @Test
    void testHandleMethodArgumentNotValidException() {
        MethodArgumentNotValidException exception = mockMethodArgumentNotValidException();

        Object response = handler.handleMethodArgumentNotValidException(exception, request);

        assertInstanceOf(R.class, response);
        R<?> r = (R<?>) response;
        AssertUtils.assertEquals(ErrorCode.PARAM_INVALID.getCode(), r.getCode());
        AssertUtils.assertEquals(ErrorCode.PARAM_INVALID.getMessage(), r.getMessage());
        assertNotNull(r.getData());
        assertInstanceOf(ArrayList.class, r.getData());
        assertEquals(List.of("username: 用户名不能为空", "email: 邮箱格式不正确"), r.getData());
    }

    @Test
    void testHandleBindException() {
        BindException exception = mockBindException();

        Object response = handler.handleBindException(exception, request);

        assertInstanceOf(R.class, response);
        R<?> r = (R<?>) response;
        AssertUtils.assertEquals(ErrorCode.PARAM_INVALID.getCode(), r.getCode());
        AssertUtils.assertEquals(ErrorCode.PARAM_INVALID.getMessage(), r.getMessage());
        assertNotNull(r.getData());
        assertEquals(List.of("用户名不能为空"), r.getData());
    }

    @Test
    void method_argument_validation_keeps_raw_response_data_but_sanitizes_logs() {
        String maliciousField = "user\nname";
        String maliciousMessage = "用户名不能为空\n<script>alert('xss')</script>";
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(new FieldError("test", maliciousField, maliciousMessage)));
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(mock(MethodParameter.class), bindingResult);
        Logger logger = (Logger) LoggerFactory.getLogger(MimirExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            R<?> response = (R<?>) handler.handleMethodArgumentNotValidException(exception, request);

            assertEquals(List.of(maliciousField + ": " + maliciousMessage), response.getData());
            assertTrue(appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .noneMatch(message -> message.contains(maliciousField) || message.contains(maliciousMessage)));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void testHandleMissingServletRequestParameterException() {
        MissingServletRequestParameterException exception =
                new MissingServletRequestParameterException("userId", "String");

        Object response = handler.handleMissingServletRequestParameterException(exception, request);

        assertInstanceOf(R.class, response);
        R<?> r = (R<?>) response;
        AssertUtils.assertEquals(ErrorCode.PARAM_MISSING.getCode(), r.getCode());
        assertTrue(r.getMessage().contains("userId"));
    }

    @Test
    void testHandleMethodArgumentTypeMismatchException() {
        MethodArgumentTypeMismatchException exception = mock(MethodArgumentTypeMismatchException.class);
        when(exception.getName()).thenReturn("userId");
        when(exception.getRequiredType()).thenAnswer(invocation -> Integer.class);

        Object response = handler.handleMethodArgumentTypeMismatchException(exception, request);

        assertInstanceOf(R.class, response);
        R<?> r = (R<?>) response;
        AssertUtils.assertEquals(ErrorCode.PARAM_INVALID.getCode(), r.getCode());
        assertTrue(r.getMessage().contains("userId"));
        assertTrue(r.getMessage().contains("Integer"));
    }

    @Test
    @SuppressWarnings("deprecation")
    void testHandleHttpMessageNotReadableException() {
        HttpMessageNotReadableException exception =
                new HttpMessageNotReadableException("JSON parse error", new Exception());

        Object response = handler.handleHttpMessageNotReadableException(exception, request);

        assertInstanceOf(R.class, response);
        R<?> r = (R<?>) response;
        AssertUtils.assertEquals(ErrorCode.PARAM_INVALID.getCode(), r.getCode());
        AssertUtils.assertEquals("请求体格式错误", r.getMessage());
    }

    @Test
    void testHandleHttpRequestMethodNotSupportedException() {
        HttpRequestMethodNotSupportedException exception = mock(HttpRequestMethodNotSupportedException.class);
        when(exception.getMethod()).thenReturn("DELETE");
        when(exception.getSupportedMethods()).thenReturn(new String[]{"GET", "POST"});

        Object response = handler.handleHttpRequestMethodNotSupportedException(exception, request);

        assertInstanceOf(R.class, response);
        R<?> r = (R<?>) response;
        AssertUtils.assertEquals(ErrorCode.OPERATION_NOT_ALLOWED.getCode(), r.getCode());
        assertTrue(r.getMessage().contains("DELETE"));
        assertTrue(r.getMessage().contains("GET"));
    }

    @Test
    void testHandleNoHandlerFoundExceptionWithNullURL() {
        NoHandlerFoundException exception = mock(NoHandlerFoundException.class);
        when(exception.getHttpMethod()).thenReturn("GET");
        when(exception.getRequestURL()).thenReturn(null);

        Object response = handler.handleNoHandlerFoundException(exception, request);

        assertInstanceOf(R.class, response);
        R<?> r = (R<?>) response;
        AssertUtils.assertEquals(ErrorCode.DATA_NOT_FOUND.getCode(), r.getCode());
        assertTrue(r.getMessage().contains("GET"));
        assertTrue(r.getMessage().contains("null"));
    }

    @Test
    void testHandleExceptionWithIException() {
        IException exception = new BaseException("99999", "未知异常") {};

        Object response = handler.handleException((Exception) exception, request);

        assertInstanceOf(R.class, response);
        R<?> r = (R<?>) response;
        AssertUtils.assertEquals("99999", r.getCode());
        AssertUtils.assertEquals("未知异常", r.getMessage());
    }

    @Test
    void testHandleExceptionWithoutIException() {
        Exception exception = new RuntimeException("普通运行时异常");

        Object response = handler.handleException(exception, request);

        assertInstanceOf(R.class, response);
        R<?> r = (R<?>) response;
        AssertUtils.assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), r.getCode());
        AssertUtils.assertEquals(ErrorCode.SYSTEM_ERROR.getMessage(), r.getMessage());
    }

    @Test
    void testHandleNullPointerException() {
        NullPointerException exception = new NullPointerException("空指针异常");

        Object response = handler.handleException(exception, request);

        assertInstanceOf(R.class, response);
        R<?> r = (R<?>) response;
        AssertUtils.assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), r.getCode());
        AssertUtils.assertEquals(ErrorCode.SYSTEM_ERROR.getMessage(), r.getMessage());
    }

    @Test
    void testSanitizeForLog() {
        when(request.getRequestURI()).thenReturn("/test/api\n<script>alert('xss')</script>");
        BizException exception = new BizException("20001", "正常消息");

        assertDoesNotThrow(() -> {
            Object response = handler.handleBizException(exception, request);
            assertNotNull(response);
        });
    }

    @Test
    void testHandleExceptionWithSpecialCharacters() {
        MissingServletRequestParameterException exception =
                new MissingServletRequestParameterException("user\nName", "String");

        Object response = handler.handleMissingServletRequestParameterException(exception, request);

        assertInstanceOf(R.class, response);
        R<?> r = (R<?>) response;
        AssertUtils.assertEquals(ErrorCode.PARAM_MISSING.getCode(), r.getCode());
        assertNotNull(r.getMessage());
    }

    @Test
    void testHandleBindExceptionWithEmptyErrors() {
        BindException exception = mockBindExceptionWithEmptyErrors();

        Object response = handler.handleBindException(exception, request);

        assertInstanceOf(R.class, response);
        R<?> r = (R<?>) response;
        AssertUtils.assertEquals(ErrorCode.PARAM_INVALID.getCode(), r.getCode());
        assertNotNull(r.getData());
        assertInstanceOf(ArrayList.class, r.getData());
        assertTrue(((ArrayList<?>) r.getData()).isEmpty());
    }

    /**
     * 测试自定义 factory 被正确调用
     */
    @Test
    void shouldDelegateToCustomFactory() {
        ExceptionResponseFactory mockFactory = mock(ExceptionResponseFactory.class);
        Object customResponse = "custom-response";
        when(mockFactory.createResponse(anyString(), anyString(), any())).thenReturn(customResponse);

        MimirExceptionHandler customHandler = new MimirExceptionHandler(mockFactory);
        BizException exception = new BizException("20001", "测试");

        Object response = customHandler.handleBizException(exception, request);

        assertSame(customResponse, response);
        verify(mockFactory).createResponse("20001", "测试", null);
    }

    @Test
    void shouldMapHandlerMethodInputValidationToBadRequest() throws Exception {
        HandlerMethodValidationException exception = mock(HandlerMethodValidationException.class);
        when(exception.isForReturnValue()).thenReturn(false);

        assertResponseEntity(handler.handleHandlerMethodValidationException(exception, request),
                HttpStatus.BAD_REQUEST, ErrorCode.PARAM_INVALID.getCode());
    }

    @Test
    void shouldMapHandlerMethodReturnValidationToInternalServerError() throws Exception {
        HandlerMethodValidationException exception = mock(HandlerMethodValidationException.class);
        when(exception.isForReturnValue()).thenReturn(true);

        assertResponseEntity(handler.handleHandlerMethodValidationException(exception, request),
                HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.SYSTEM_ERROR.getCode());
    }

    @Test
    void shouldMapConstraintViolationToBadRequest() throws Exception {
        assertResponseCode(handler.handleConstraintViolationException(new ConstraintViolationException(Collections.emptySet()), request),
                ErrorCode.PARAM_INVALID.getCode());
        assertResponseStatus("handleConstraintViolationException", ConstraintViolationException.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldMapMissingRequestHeaderToBadRequest() throws Exception {
        assertResponseCode(handler.handleMissingRequestHeaderException(
                new MissingRequestHeaderException("X-Request-Id", mock(MethodParameter.class)), request),
                ErrorCode.PARAM_MISSING.getCode());
        assertResponseStatus("handleMissingRequestHeaderException", MissingRequestHeaderException.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldMapMissingPathVariableToInternalServerError() throws Exception {
        assertResponseCode(handler.handleMissingPathVariableException(
                new MissingPathVariableException("id", mock(MethodParameter.class)), request),
                ErrorCode.SYSTEM_ERROR.getCode());
        assertResponseStatus("handleMissingPathVariableException", MissingPathVariableException.class,
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void shouldMapNotAcceptableMediaType() throws Exception {
        assertResponseCode(handler.handleHttpMediaTypeNotAcceptableException(
                new HttpMediaTypeNotAcceptableException(Collections.emptyList()), request),
                ErrorCode.OPERATION_NOT_ALLOWED.getCode());
        assertResponseStatus("handleHttpMediaTypeNotAcceptableException", HttpMediaTypeNotAcceptableException.class,
                HttpStatus.NOT_ACCEPTABLE);
    }

    @Test
    void shouldMapUnsupportedMediaType() throws Exception {
        assertResponseCode(handler.handleHttpMediaTypeNotSupportedException(
                new HttpMediaTypeNotSupportedException("unsupported"), request),
                ErrorCode.OPERATION_NOT_ALLOWED.getCode());
        assertResponseStatus("handleHttpMediaTypeNotSupportedException", HttpMediaTypeNotSupportedException.class,
                HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void shouldMapMaxUploadSize() throws Exception {
        assertResponseCode(handler.handleMaxUploadSizeExceededException(new MaxUploadSizeExceededException(1024), request),
                ErrorCode.PARAM_INVALID.getCode());
        assertResponseStatus("handleMaxUploadSizeExceededException", MaxUploadSizeExceededException.class,
                HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @Test
    void shouldMapNoResourceFound() throws Exception {
        assertResponseCode(handler.handleNoResourceFoundException(
                new NoResourceFoundException(HttpMethod.GET, "missing.js"), request),
                ErrorCode.DATA_NOT_FOUND.getCode());
        assertResponseStatus("handleNoResourceFoundException", NoResourceFoundException.class, HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldDelegateAllSpringSixMappingsToResponseFactory() {
        ExceptionResponseFactory responseFactory = mock(ExceptionResponseFactory.class);
        when(responseFactory.createResponse(anyString(), anyString(), any())).thenReturn("custom-response");
        MimirExceptionHandler customHandler = new MimirExceptionHandler(responseFactory);
        HandlerMethodValidationException validationException = mock(HandlerMethodValidationException.class);
        when(validationException.isForReturnValue()).thenReturn(false);

        Object validationResponse = customHandler.handleHandlerMethodValidationException(validationException, request);
        assertInstanceOf(ResponseEntity.class, validationResponse);
        assertEquals("custom-response", ((ResponseEntity<?>) validationResponse).getBody());
        assertResponseEntityBody(customHandler.handleConstraintViolationException(
                new ConstraintViolationException(Collections.emptySet()), request), HttpStatus.BAD_REQUEST);
        assertResponseEntityBody(customHandler.handleMissingRequestHeaderException(
                new MissingRequestHeaderException("X-Request-Id", mock(MethodParameter.class)), request), HttpStatus.BAD_REQUEST);
        assertResponseEntityBody(customHandler.handleMissingPathVariableException(
                new MissingPathVariableException("id", mock(MethodParameter.class)), request), HttpStatus.INTERNAL_SERVER_ERROR);
        assertResponseEntityBody(customHandler.handleHttpMediaTypeNotAcceptableException(
                new HttpMediaTypeNotAcceptableException(Collections.emptyList()), request), HttpStatus.NOT_ACCEPTABLE);
        assertResponseEntityBody(customHandler.handleHttpMediaTypeNotSupportedException(
                new HttpMediaTypeNotSupportedException("unsupported"), request), HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertResponseEntityBody(customHandler.handleMaxUploadSizeExceededException(
                new MaxUploadSizeExceededException(1024), request), HttpStatus.PAYLOAD_TOO_LARGE);
        assertResponseEntityBody(customHandler.handleNoResourceFoundException(
                new NoResourceFoundException(HttpMethod.GET, "missing.js"), request), HttpStatus.NOT_FOUND);

        verify(responseFactory, times(8)).createResponse(anyString(), anyString(), isNull());
    }

    // ========== fallback 测试：factory 抛异常时降级返回 R.fail ==========

    private MimirExceptionHandler createHandlerWithFailingFactory() {
        ExceptionResponseFactory failingFactory = (code, message, data) -> {
            throw new RuntimeException("factory error");
        };
        return new MimirExceptionHandler(failingFactory);
    }

    @Test
    void shouldPreserveStatusAndFallbackWhenNewHandlerFactoryFails() {
        MimirExceptionHandler failHandler = createHandlerWithFailingFactory();
        HandlerMethodValidationException validationException = mock(HandlerMethodValidationException.class);
        when(validationException.isForReturnValue()).thenReturn(true);

        assertResponseEntity(failHandler.handleHandlerMethodValidationException(validationException, request),
                HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.SYSTEM_ERROR.getCode());
        assertResponseEntity(failHandler.handleNoResourceFoundException(
                new NoResourceFoundException(HttpMethod.GET, "missing.js"), request),
                HttpStatus.NOT_FOUND, ErrorCode.DATA_NOT_FOUND.getCode());
    }

    @Test
    void shouldPreserveStaticStatusInMvcWhenFactoryReturnsResponseEntity() throws Exception {
        reset(request);
        ExceptionResponseFactory factory = (code, message, data) -> ResponseEntity.ok("factory-response");
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new MimirExceptionHandler(factory))
                .build();

        mvc.perform(get("/missing-resource"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("factory-response"));
    }

    @Test
    void shouldFallbackToRFailWhenFactoryThrowsOnSystemException() {
        MimirExceptionHandler failHandler = createHandlerWithFailingFactory();
        SystemException exception = new SystemException("SYS_001", "系统错误");

        Object result = failHandler.handleSystemException(exception, request);

        assertInstanceOf(R.class, result);
        R<?> r = (R<?>) result;
        assertEquals("SYS_001", r.getCode());
        assertEquals("系统错误", r.getMessage());
    }

    @Test
    void shouldFallbackToRFailWhenFactoryThrowsOnBaseException() {
        MimirExceptionHandler failHandler = createHandlerWithFailingFactory();
        BaseException exception = new BaseException("BASE_001", "基础异常") {};

        Object result = failHandler.handleBaseException(exception, request);

        assertInstanceOf(R.class, result);
        R<?> r = (R<?>) result;
        assertEquals("BASE_001", r.getCode());
    }

    @Test
    void shouldFallbackToRFailWhenFactoryThrowsOnMethodArgumentNotValidException() {
        MimirExceptionHandler failHandler = createHandlerWithFailingFactory();
        MethodArgumentNotValidException exception = mockMethodArgumentNotValidException();

        Object result = failHandler.handleMethodArgumentNotValidException(exception, request);

        assertInstanceOf(R.class, result);
        R<?> r = (R<?>) result;
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), r.getCode());
    }

    @Test
    void shouldFallbackToRFailWhenFactoryThrowsOnBindException() {
        MimirExceptionHandler failHandler = createHandlerWithFailingFactory();
        BindException exception = mockBindException();

        Object result = failHandler.handleBindException(exception, request);

        assertInstanceOf(R.class, result);
        R<?> r = (R<?>) result;
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), r.getCode());
    }

    @Test
    void shouldFallbackToRFailWhenFactoryThrowsOnMissingServletRequestParameterException() {
        MimirExceptionHandler failHandler = createHandlerWithFailingFactory();
        MissingServletRequestParameterException exception =
                new MissingServletRequestParameterException("userId", "String");

        Object result = failHandler.handleMissingServletRequestParameterException(exception, request);

        assertInstanceOf(R.class, result);
        R<?> r = (R<?>) result;
        assertEquals(ErrorCode.PARAM_MISSING.getCode(), r.getCode());
    }

    @Test
    void shouldFallbackToRFailWhenFactoryThrowsOnHandleException() {
        MimirExceptionHandler failHandler = createHandlerWithFailingFactory();
        Exception exception = new RuntimeException("普通异常");

        Object result = failHandler.handleException(exception, request);

        assertInstanceOf(R.class, result);
        R<?> r = (R<?>) result;
        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), r.getCode());
    }

    @Test
    void shouldFallbackToRFailWhenFactoryThrowsOnHandleExceptionWithIException() {
        MimirExceptionHandler failHandler = createHandlerWithFailingFactory();
        IException exception = new BaseException("IE_001", "IException异常") {};

        Object result = failHandler.handleException((Exception) exception, request);

        assertInstanceOf(R.class, result);
        R<?> r = (R<?>) result;
        assertEquals("IE_001", r.getCode());
    }

    // ========== null-safe 分支测试 ==========

    @Test
    void testHandleHttpRequestMethodNotSupportedExceptionWithNullSupportedMethods() {
        HttpRequestMethodNotSupportedException exception = mock(HttpRequestMethodNotSupportedException.class);
        when(exception.getMethod()).thenReturn("DELETE");
        when(exception.getSupportedMethods()).thenReturn(null);

        Object response = handler.handleHttpRequestMethodNotSupportedException(exception, request);

        assertInstanceOf(R.class, response);
        R<?> r = (R<?>) response;
        assertEquals(ErrorCode.OPERATION_NOT_ALLOWED.getCode(), r.getCode());
        assertTrue(r.getMessage().contains("DELETE"));
    }

    @Test
    void testHandleMethodArgumentTypeMismatchExceptionWithNullRequiredType() {
        MethodArgumentTypeMismatchException exception = mock(MethodArgumentTypeMismatchException.class);
        when(exception.getName()).thenReturn("userId");
        when(exception.getRequiredType()).thenReturn(null);

        Object response = handler.handleMethodArgumentTypeMismatchException(exception, request);

        assertInstanceOf(R.class, response);
        R<?> r = (R<?>) response;
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), r.getCode());
        assertTrue(r.getMessage().contains("unknown"));
    }

    @Test
    void testHandleNoHandlerFoundExceptionWithNormalURL() {
        NoHandlerFoundException exception = mock(NoHandlerFoundException.class);
        when(exception.getHttpMethod()).thenReturn("GET");
        when(exception.getRequestURL()).thenReturn("http://localhost/api/users");

        Object response = handler.handleNoHandlerFoundException(exception, request);

        assertInstanceOf(R.class, response);
        R<?> r = (R<?>) response;
        assertEquals(ErrorCode.DATA_NOT_FOUND.getCode(), r.getCode());
        assertTrue(r.getMessage().contains("GET"));
        assertTrue(r.getMessage().contains("http://localhost/api/users"));
    }

    @Test
    void shouldFallbackToRFailWhenFactoryThrowsOnMethodArgumentTypeMismatch() {
        MimirExceptionHandler failHandler = createHandlerWithFailingFactory();
        MethodArgumentTypeMismatchException exception = mock(MethodArgumentTypeMismatchException.class);
        when(exception.getName()).thenReturn("userId");
        when(exception.getRequiredType()).thenAnswer(invocation -> Integer.class);

        Object result = failHandler.handleMethodArgumentTypeMismatchException(exception, request);

        assertInstanceOf(R.class, result);
        R<?> r = (R<?>) result;
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), r.getCode());
    }

    @Test
    @SuppressWarnings("deprecation")
    void shouldFallbackToRFailWhenFactoryThrowsOnHttpMessageNotReadable() {
        MimirExceptionHandler failHandler = createHandlerWithFailingFactory();
        HttpMessageNotReadableException exception =
                new HttpMessageNotReadableException("JSON parse error", new Exception());

        Object result = failHandler.handleHttpMessageNotReadableException(exception, request);

        assertInstanceOf(R.class, result);
        R<?> r = (R<?>) result;
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), r.getCode());
    }

    @Test
    void shouldFallbackToRFailWhenFactoryThrowsOnHttpRequestMethodNotSupported() {
        MimirExceptionHandler failHandler = createHandlerWithFailingFactory();
        HttpRequestMethodNotSupportedException exception = mock(HttpRequestMethodNotSupportedException.class);
        when(exception.getMethod()).thenReturn("DELETE");
        when(exception.getSupportedMethods()).thenReturn(new String[]{"GET", "POST"});

        Object result = failHandler.handleHttpRequestMethodNotSupportedException(exception, request);

        assertInstanceOf(R.class, result);
        R<?> r = (R<?>) result;
        assertEquals(ErrorCode.OPERATION_NOT_ALLOWED.getCode(), r.getCode());
    }

    @Test
    void shouldFallbackToRFailWhenFactoryThrowsOnNoHandlerFound() {
        MimirExceptionHandler failHandler = createHandlerWithFailingFactory();
        NoHandlerFoundException exception = mock(NoHandlerFoundException.class);
        when(exception.getHttpMethod()).thenReturn("GET");
        when(exception.getRequestURL()).thenReturn("http://localhost/api/users");

        Object result = failHandler.handleNoHandlerFoundException(exception, request);

        assertInstanceOf(R.class, result);
        R<?> r = (R<?>) result;
        assertEquals(ErrorCode.DATA_NOT_FOUND.getCode(), r.getCode());
    }

    // ========== 辅助方法 ==========

    private MethodArgumentNotValidException mockMethodArgumentNotValidException() {
        BindingResult bindingResult = mock(BindingResult.class);
        List<FieldError> fieldErrors = new ArrayList<>();
        fieldErrors.add(new FieldError("test", "username", "用户名不能为空"));
        fieldErrors.add(new FieldError("test", "email", "邮箱格式不正确"));
        when(bindingResult.getFieldErrors()).thenReturn(fieldErrors);

        MethodParameter methodParameter = mock(MethodParameter.class);
        return new MethodArgumentNotValidException(methodParameter, bindingResult);
    }

    private BindException mockBindException() {
        BindingResult bindingResult = mock(BindingResult.class);
        List<FieldError> fieldErrors = new ArrayList<>();
        fieldErrors.add(new FieldError("test", "username", "用户名不能为空"));
        when(bindingResult.getFieldErrors()).thenReturn(fieldErrors);

        return new BindException(bindingResult);
    }

    private BindException mockBindExceptionWithEmptyErrors() {
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(Collections.emptyList());

        return new BindException(bindingResult);
    }

    private void assertResponseCode(Object response, String code) {
        if (response instanceof ResponseEntity<?> entity) {
            assertResponseCode(entity.getBody(), code);
            return;
        }
        assertInstanceOf(R.class, response);
        assertEquals(code, ((R<?>) response).getCode());
    }

    private void assertResponseEntity(Object response, HttpStatus status, String code) {
        assertInstanceOf(ResponseEntity.class, response);
        ResponseEntity<?> entity = (ResponseEntity<?>) response;
        assertEquals(status, entity.getStatusCode());
        assertResponseCode(entity.getBody(), code);
    }

    private void assertResponseEntityBody(Object response, HttpStatus status) {
        assertInstanceOf(ResponseEntity.class, response);
        ResponseEntity<?> entity = (ResponseEntity<?>) response;
        assertEquals(status, entity.getStatusCode());
        assertEquals("custom-response", entity.getBody());
    }

    @RestController
    private static final class ThrowingController {

        @GetMapping("/missing-resource")
        String missingResource() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "missing.js");
        }
    }

    private void assertResponseStatus(String methodName, Class<? extends Exception> exceptionType, HttpStatus status)
            throws NoSuchMethodException {
        Method method = MimirExceptionHandler.class.getMethod(methodName, exceptionType, HttpServletRequest.class);
        assertNull(method.getAnnotation(ResponseStatus.class));
    }

    @Test
    void shouldFallbackToRFailWhenFactoryThrowsException() {
        ExceptionResponseFactory failingFactory = (code, message, data) -> {
            throw new RuntimeException("factory error");
        };
        MimirExceptionHandler failHandler = new MimirExceptionHandler(failingFactory);

        BizException bizException = new BizException("BIZ_001", "业务错误");
        Object result = failHandler.handleBizException(bizException, request);

        assertNotNull(result);
        assertInstanceOf(R.class, result);
        R<?> r = (R<?>) result;
        assertEquals("BIZ_001", r.getCode());
    }
}
