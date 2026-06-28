package com.yggdrasil.labs.exception.handler;

import com.yggdrasil.labs.common.exception.*;
import com.yggdrasil.labs.common.response.R;
import com.yggdrasil.labs.test.base.BaseUnitTest;
import com.yggdrasil.labs.test.util.AssertUtils;
import com.yggdrasil.labs.test.util.TestUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

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
        assertFalse(((ArrayList<?>) r.getData()).isEmpty());
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

    // ========== fallback 测试：factory 抛异常时降级返回 R.fail ==========

    private MimirExceptionHandler createHandlerWithFailingFactory() {
        ExceptionResponseFactory failingFactory = (code, message, data) -> {
            throw new RuntimeException("factory error");
        };
        return new MimirExceptionHandler(failingFactory);
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
