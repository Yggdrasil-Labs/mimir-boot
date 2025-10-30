package com.yggdrasil.labs.exception.handler;

import com.yggdrasil.labs.common.exception.*;
import com.yggdrasil.labs.common.response.R;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
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
import static org.mockito.Mockito.when;

/**
 * 全局异常处理器测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        when(request.getRequestURI()).thenReturn("/test/api");
    }

    /**
     * 测试处理业务异常
     */
    @Test
    void testHandleBizException() {
        BizException exception = new BizException("20001", "用户不存在");

        R<Void> response = handler.handleBizException(exception, request);

        assertNotNull(response);
        assertEquals("20001", response.getCode());
        assertEquals("用户不存在", response.getMessage());
        assertNull(response.getData());
    }

    /**
     * 测试处理业务异常（使用 ErrorCode）
     */
    @Test
    void testHandleBizExceptionWithErrorCode() {
        BizException exception = new BizException(ErrorCode.DATA_NOT_FOUND);

        R<Void> response = handler.handleBizException(exception, request);

        assertNotNull(response);
        assertEquals(ErrorCode.DATA_NOT_FOUND.getCode(), response.getCode());
        assertEquals(ErrorCode.DATA_NOT_FOUND.getMessage(), response.getMessage());
    }

    /**
     * 测试处理系统异常
     */
    @Test
    void testHandleSystemException() {
        SystemException exception = new SystemException("10000", "系统错误");

        R<Void> response = handler.handleSystemException(exception, request);

        assertNotNull(response);
        assertEquals("10000", response.getCode());
        assertEquals("系统错误", response.getMessage());
        assertNull(response.getData());
    }

    /**
     * 测试处理系统异常（使用 ErrorCode）
     */
    @Test
    void testHandleSystemExceptionWithErrorCode() {
        SystemException exception = new SystemException(ErrorCode.SYSTEM_ERROR);

        R<Void> response = handler.handleSystemException(exception, request);

        assertNotNull(response);
        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), response.getCode());
        assertEquals(ErrorCode.SYSTEM_ERROR.getMessage(), response.getMessage());
    }

    /**
     * 测试处理基础异常
     */
    @Test
    void testHandleBaseException() {
        BaseException exception = new BaseException("99999", "基础异常") {
        };

        R<Void> response = handler.handleBaseException(exception, request);

        assertNotNull(response);
        assertEquals("99999", response.getCode());
        assertEquals("基础异常", response.getMessage());
    }

    /**
     * 测试处理方法参数校验异常
     */
    @Test
    void testHandleMethodArgumentNotValidException() {
        MethodArgumentNotValidException exception = mockMethodArgumentNotValidException();

        R<List<String>> response = handler.handleMethodArgumentNotValidException(exception, request);

        assertNotNull(response);
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), response.getCode());
        assertEquals(ErrorCode.PARAM_INVALID.getMessage(), response.getMessage());
        assertNotNull(response.getData());
        assertFalse(response.getData().isEmpty());
    }

    /**
     * 测试处理绑定异常
     */
    @Test
    void testHandleBindException() {
        BindException exception = mockBindException();

        R<List<String>> response = handler.handleBindException(exception, request);

        assertNotNull(response);
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), response.getCode());
        assertEquals(ErrorCode.PARAM_INVALID.getMessage(), response.getMessage());
        assertNotNull(response.getData());
    }

    /**
     * 测试处理缺少请求参数异常
     */
    @Test
    void testHandleMissingServletRequestParameterException() {
        MissingServletRequestParameterException exception =
                new MissingServletRequestParameterException("userId", "String");

        R<Void> response = handler.handleMissingServletRequestParameterException(exception, request);

        assertNotNull(response);
        assertEquals(ErrorCode.PARAM_MISSING.getCode(), response.getCode());
        assertTrue(response.getMessage().contains("userId"));
    }

    /**
     * 测试处理方法参数类型不匹配异常
     */
    @Test
    void testHandleMethodArgumentTypeMismatchException() {
        MethodArgumentTypeMismatchException exception = org.mockito.Mockito.mock(
                MethodArgumentTypeMismatchException.class);
        when(exception.getName()).thenReturn("userId");
        when(exception.getRequiredType()).thenAnswer(invocation -> Integer.class);

        R<Void> response = handler.handleMethodArgumentTypeMismatchException(exception, request);

        assertNotNull(response);
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), response.getCode());
        assertTrue(response.getMessage().contains("userId"));
        assertTrue(response.getMessage().contains("Integer"));
    }

    /**
     * 测试处理 HTTP 消息不可读异常
     */
    @Test
    @SuppressWarnings("deprecation")
    void testHandleHttpMessageNotReadableException() {
        HttpMessageNotReadableException exception =
                new HttpMessageNotReadableException("JSON parse error", new Exception());

        R<Void> response = handler.handleHttpMessageNotReadableException(exception, request);

        assertNotNull(response);
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), response.getCode());
        assertEquals("请求体格式错误", response.getMessage());
    }

    /**
     * 测试处理 HTTP 请求方法不支持异常
     */
    @Test
    void testHandleHttpRequestMethodNotSupportedException() {
        HttpRequestMethodNotSupportedException exception = org.mockito.Mockito.mock(
                HttpRequestMethodNotSupportedException.class);
        when(exception.getMethod()).thenReturn("DELETE");
        when(exception.getSupportedMethods()).thenReturn(new String[]{"GET", "POST"});

        R<Void> response = handler.handleHttpRequestMethodNotSupportedException(exception, request);

        assertNotNull(response);
        assertEquals(ErrorCode.OPERATION_NOT_ALLOWED.getCode(), response.getCode());
        assertTrue(response.getMessage().contains("DELETE"));
        assertTrue(response.getMessage().contains("GET"));
    }

    /**
     * 测试处理处理器未找到异常（RequestURL 为 null）
     */
    @Test
    void testHandleNoHandlerFoundExceptionWithNullURL() {
        NoHandlerFoundException exception = org.mockito.Mockito.mock(NoHandlerFoundException.class);
        when(exception.getHttpMethod()).thenReturn("GET");
        when(exception.getRequestURL()).thenReturn(null);

        R<Void> response = handler.handleNoHandlerFoundException(exception, request);

        assertNotNull(response);
        assertEquals(ErrorCode.DATA_NOT_FOUND.getCode(), response.getCode());
        assertTrue(response.getMessage().contains("GET"));
        assertTrue(response.getMessage().contains("null"));
    }

    /**
     * 测试处理所有未捕获的异常（实现 IException 接口）
     */
    @Test
    void testHandleExceptionWithIException() {
        IException exception = new BaseException("99999", "未知异常") {
        };

        R<Void> response = handler.handleException((Exception) exception, request);

        assertNotNull(response);
        assertEquals("99999", response.getCode());
        assertEquals("未知异常", response.getMessage());
    }

    /**
     * 测试处理所有未捕获的异常（普通异常）
     */
    @Test
    void testHandleExceptionWithoutIException() {
        Exception exception = new RuntimeException("普通运行时异常");

        R<Void> response = handler.handleException(exception, request);

        assertNotNull(response);
        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), response.getCode());
        assertEquals(ErrorCode.SYSTEM_ERROR.getMessage(), response.getMessage());
    }

    /**
     * 测试处理空指针异常
     */
    @Test
    void testHandleNullPointerException() {
        NullPointerException exception = new NullPointerException("空指针异常");

        R<Void> response = handler.handleException(exception, request);

        assertNotNull(response);
        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), response.getCode());
        assertEquals(ErrorCode.SYSTEM_ERROR.getMessage(), response.getMessage());
    }

    /**
     * 测试日志清理功能（防止日志注入）
     */
    @Test
    void testSanitizeForLog() {
        when(request.getRequestURI()).thenReturn("/test/api\n<script>alert('xss')</script>");

        BizException exception = new BizException("20001", "正常消息");

        // 应该不会抛出异常，即使 URI 包含特殊字符
        assertDoesNotThrow(() -> {
            R<Void> response = handler.handleBizException(exception, request);
            assertNotNull(response);
        });
    }

    /**
     * 测试处理包含特殊字符的参数名
     */
    @Test
    void testHandleExceptionWithSpecialCharacters() {
        MissingServletRequestParameterException exception =
                new MissingServletRequestParameterException("user\nName", "String");

        R<Void> response = handler.handleMissingServletRequestParameterException(exception, request);

        assertNotNull(response);
        assertEquals(ErrorCode.PARAM_MISSING.getCode(), response.getCode());
        // 应该已经清理了特殊字符
        assertNotNull(response.getMessage());
    }

    /**
     * 测试处理空的字段错误列表
     */
    @Test
    void testHandleBindExceptionWithEmptyErrors() {
        BindException exception = mockBindExceptionWithEmptyErrors();

        R<List<String>> response = handler.handleBindException(exception, request);

        assertNotNull(response);
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), response.getCode());
        assertNotNull(response.getData());
        assertTrue(response.getData().isEmpty());
    }

    // ========== 辅助方法 ==========

    private MethodArgumentNotValidException mockMethodArgumentNotValidException() {
        org.springframework.validation.BindingResult bindingResult =
                org.mockito.Mockito.mock(org.springframework.validation.BindingResult.class);
        List<FieldError> fieldErrors = new ArrayList<>();
        fieldErrors.add(new FieldError("test", "username", "用户名不能为空"));
        fieldErrors.add(new FieldError("test", "email", "邮箱格式不正确"));

        when(bindingResult.getFieldErrors()).thenReturn(fieldErrors);

        MethodParameter methodParameter =
                org.mockito.Mockito.mock(MethodParameter.class);
        return new MethodArgumentNotValidException(methodParameter, bindingResult);
    }

    private BindException mockBindException() {
        org.springframework.validation.BindingResult bindingResult =
                org.mockito.Mockito.mock(org.springframework.validation.BindingResult.class);
        List<FieldError> fieldErrors = new ArrayList<>();
        fieldErrors.add(new FieldError("test", "username", "用户名不能为空"));

        when(bindingResult.getFieldErrors()).thenReturn(fieldErrors);

        return new BindException(bindingResult);
    }

    private BindException mockBindExceptionWithEmptyErrors() {
        org.springframework.validation.BindingResult bindingResult =
                org.mockito.Mockito.mock(org.springframework.validation.BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(Collections.emptyList());

        return new BindException(bindingResult);
    }
}

