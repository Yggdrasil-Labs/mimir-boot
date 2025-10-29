package com.yggdrasil.labs.exception.handler;

import com.yggdrasil.labs.common.exception.*;
import com.yggdrasil.labs.common.response.R;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * <p>
 * 统一处理应用程序中的所有异常，返回统一的响应格式。
 * </p>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     *
     * @param e       业务异常
     * @param request HTTP 请求
     * @return 错误响应
     */
    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleBizException(BizException e, HttpServletRequest request) {
        log.warn("业务异常: code={}, message={}, uri={}", e.getCode(), e.getMessage(), request.getRequestURI());
        return R.fail(e.getCode(), e.getMessage());
    }

    /**
     * 处理系统异常
     *
     * @param e       系统异常
     * @param request HTTP 请求
     * @return 错误响应
     */
    @ExceptionHandler(SystemException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleSystemException(SystemException e, HttpServletRequest request) {
        log.error("系统异常: code={}, message={}, uri={}", e.getCode(), e.getMessage(), request.getRequestURI(), e);
        return R.fail(e.getCode(), e.getMessage());
    }

    /**
     * 处理基础异常（兜底处理实现 IException 的异常）
     * <p>
     * 处理所有 BaseException 及其子类但未被其他处理器捕获的异常。
     * 注意：此处理器优先级低于具体的异常类型处理器。
     * </p>
     *
     * @param e       基础异常
     * @param request HTTP 请求
     * @return 错误响应
     */
    @ExceptionHandler(BaseException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleBaseException(BaseException e, HttpServletRequest request) {
        log.error("框架异常: code={}, message={}, uri={}", e.getCode(), e.getMessage(),
                request.getRequestURI(), e);
        return R.fail(e.getCode(), e.getMessage());
    }


    /**
     * 处理方法参数校验异常（@Valid）
     *
     * @param e       方法参数校验异常
     * @param request HTTP 请求
     * @return 错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<List<String>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException e, HttpServletRequest request) {
        List<String> errors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.toList());
        log.warn("参数校验异常: errors={}, uri={}", errors, request.getRequestURI());
        return new R<>(
                ErrorCode.PARAM_INVALID.getCode(),
                ErrorCode.PARAM_INVALID.getMessage(),
                errors
        );
    }

    /**
     * 处理绑定异常（@ModelAttribute）
     *
     * @param e       绑定异常
     * @param request HTTP 请求
     * @return 错误响应
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<List<String>> handleBindException(BindException e, HttpServletRequest request) {
        List<String> errors = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.toList());
        log.warn("参数绑定异常: errors={}, uri={}", errors, request.getRequestURI());
        return new R<>(
                ErrorCode.PARAM_INVALID.getCode(),
                ErrorCode.PARAM_INVALID.getMessage(),
                errors
        );
    }

    /**
     * 处理缺少请求参数异常
     *
     * @param e       缺少请求参数异常
     * @param request HTTP 请求
     * @return 错误响应
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException e, HttpServletRequest request) {
        String message = String.format("缺少必需参数: %s", e.getParameterName());
        log.warn("缺少请求参数异常: {}, uri={}", message, request.getRequestURI());
        return R.fail(ErrorCode.PARAM_MISSING.getCode(), message);
    }

    /**
     * 处理方法参数类型不匹配异常
     *
     * @param e       方法参数类型不匹配异常
     * @param request HTTP 请求
     * @return 错误响应
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        String message = String.format("参数类型不匹配: %s，期望类型: %s", e.getName(), Objects.requireNonNull(e.getRequiredType()).getSimpleName());
        log.warn("参数类型不匹配异常: {}, uri={}", message, request.getRequestURI());
        return R.fail(ErrorCode.PARAM_INVALID.getCode(), message);
    }

    /**
     * 处理 HTTP 消息不可读异常（JSON 解析失败等）
     *
     * @param e       HTTP 消息不可读异常
     * @param request HTTP 请求
     * @return 错误响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException e, HttpServletRequest request) {
        log.warn("HTTP 消息不可读异常: {}, uri={}", e.getMessage(), request.getRequestURI());
        return R.fail(ErrorCode.PARAM_INVALID.getCode(), "请求体格式错误");
    }

    /**
     * 处理 HTTP 请求方法不支持异常
     *
     * @param e       HTTP 请求方法不支持异常
     * @param request HTTP 请求
     * @return 错误响应
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public R<Void> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        String message = String.format("请求方法 %s 不支持，支持的方法: %s", e.getMethod(), String.join(", ", Objects.requireNonNull(e.getSupportedMethods())));
        log.warn("HTTP 请求方法不支持异常: {}, uri={}", message, request.getRequestURI());
        return R.fail(ErrorCode.OPERATION_NOT_ALLOWED.getCode(), message);
    }

    /**
     * 处理处理器未找到异常（404）
     *
     * @param e       处理器未找到异常
     * @param request HTTP 请求
     * @return 错误响应
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public R<Void> handleNoHandlerFoundException(
            NoHandlerFoundException e, HttpServletRequest request) {
        String message = String.format("未找到请求路径: %s %s", e.getHttpMethod(), e.getRequestURL());
        log.warn("处理器未找到异常: {}, uri={}", message, request.getRequestURI());
        return R.fail(ErrorCode.DATA_NOT_FOUND.getCode(), message);
    }

    /**
     * 处理所有未捕获的异常
     * <p>
     * 如果异常实现了 IException 接口，则使用其错误码和消息；否则使用默认系统错误。
     * </p>
     *
     * @param e       异常
     * @param request HTTP 请求
     * @return 错误响应
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleException(Exception e, HttpServletRequest request) {
        // 如果异常实现了 IException 接口，使用其错误码和消息
        if (e instanceof IException ie) {
            log.error("框架异常（未捕获）: code={}, message={}, uri={}",
                    ie.getCode(), ie.getMessage(), request.getRequestURI(), e);
            return R.fail(ie.getCode(), ie.getMessage());
        }

        log.error("未捕获的异常: uri={}", request.getRequestURI(), e);
        return R.fail(ErrorCode.SYSTEM_ERROR.getCode(), ErrorCode.SYSTEM_ERROR.getMessage());
    }
}

