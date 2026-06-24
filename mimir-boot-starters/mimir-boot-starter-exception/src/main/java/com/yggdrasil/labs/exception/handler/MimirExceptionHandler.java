package com.yggdrasil.labs.exception.handler;

import com.yggdrasil.labs.common.exception.*;
import com.yggdrasil.labs.common.response.R;
import com.yggdrasil.labs.common.util.LogSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.ArrayList;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Mimir 全局异常处理器
 *
 * <p>统一处理应用程序中的所有异常，通过 {@link ExceptionResponseFactory} 构建响应体。</p>
 *
 * @author Yggdrasil Labs
 * @since 2.1.0
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class MimirExceptionHandler {

    private final ExceptionResponseFactory responseFactory;

    public MimirExceptionHandler(ExceptionResponseFactory responseFactory) {
        this.responseFactory = responseFactory;
    }

    private String sanitizeForLog(String input) {
        return LogSanitizer.sanitize(input);
    }

    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.OK)
    public Object handleBizException(BizException e, HttpServletRequest request) {
        log.warn("业务异常: code={}, message={}, uri={}",
                sanitizeForLog(e.getCode()),
                sanitizeForLog(e.getMessage()),
                sanitizeForLog(request.getRequestURI()));
        String code = e.getCode();
        String message = e.getMessage();
        try {
            return responseFactory.createResponse(code, message, null);
        } catch (Exception ex) {
            log.error("responseFactory 异常，降级返回 R", ex);
            return R.fail(code, message);
        }
    }

    @ExceptionHandler(SystemException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Object handleSystemException(SystemException e, HttpServletRequest request) {
        log.error("系统异常: code={}, message={}, uri={}",
                sanitizeForLog(e.getCode()),
                sanitizeForLog(e.getMessage()),
                sanitizeForLog(request.getRequestURI()),
                e);
        String code = e.getCode();
        String message = e.getMessage();
        try {
            return responseFactory.createResponse(code, message, null);
        } catch (Exception ex) {
            log.error("responseFactory 异常，降级返回 R", ex);
            return R.fail(code, message);
        }
    }

    @ExceptionHandler(BaseException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Object handleBaseException(BaseException e, HttpServletRequest request) {
        log.error("框架异常: code={}, message={}, uri={}",
                sanitizeForLog(e.getCode()),
                sanitizeForLog(e.getMessage()),
                sanitizeForLog(request.getRequestURI()),
                e);
        String code = e.getCode();
        String message = e.getMessage();
        try {
            return responseFactory.createResponse(code, message, null);
        } catch (Exception ex) {
            log.error("responseFactory 异常，降级返回 R", ex);
            return R.fail(code, message);
        }
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Object handleMethodArgumentNotValidException(
            MethodArgumentNotValidException e, HttpServletRequest request) {
        ArrayList<String> errors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> sanitizeForLog(error.getField()) + ": " + sanitizeForLog(error.getDefaultMessage()))
                .collect(Collectors.toCollection(ArrayList::new));
        log.warn("参数校验异常: errors={}, uri={}",
                errors,
                sanitizeForLog(request.getRequestURI()));
        String code = ErrorCode.PARAM_INVALID.getCode();
        String message = ErrorCode.PARAM_INVALID.getMessage();
        try {
            return responseFactory.createResponse(code, message, errors);
        } catch (Exception ex) {
            log.error("responseFactory 异常，降级返回 R", ex);
            return R.fail(code, message);
        }
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Object handleBindException(BindException e, HttpServletRequest request) {
        ArrayList<String> errors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> sanitizeForLog(error.getDefaultMessage()))
                .collect(Collectors.toCollection(ArrayList::new));
        log.warn("参数绑定异常: errors={}, uri={}",
                errors,
                sanitizeForLog(request.getRequestURI()));
        String code = ErrorCode.PARAM_INVALID.getCode();
        String message = ErrorCode.PARAM_INVALID.getMessage();
        try {
            return responseFactory.createResponse(code, message, errors);
        } catch (Exception ex) {
            log.error("responseFactory 异常，降级返回 R", ex);
            return R.fail(code, message);
        }
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Object handleMissingServletRequestParameterException(
            MissingServletRequestParameterException e, HttpServletRequest request) {
        String sanitizedParamName = sanitizeForLog(e.getParameterName());
        String message = String.format("缺少必需参数: %s", sanitizedParamName);
        log.warn("缺少请求参数异常: {}, uri={}",
                sanitizeForLog(message),
                sanitizeForLog(request.getRequestURI()));
        String code = ErrorCode.PARAM_MISSING.getCode();
        try {
            return responseFactory.createResponse(code, message, null);
        } catch (Exception ex) {
            log.error("responseFactory 异常，降级返回 R", ex);
            return R.fail(code, message);
        }
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Object handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        String sanitizedParamName = sanitizeForLog(e.getName());
        String expectedType = e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "unknown";
        String message = String.format("参数类型不匹配: %s，期望类型: %s", sanitizedParamName, expectedType);
        log.warn("参数类型不匹配异常: {}, uri={}",
                sanitizeForLog(message),
                sanitizeForLog(request.getRequestURI()));
        String code = ErrorCode.PARAM_INVALID.getCode();
        try {
            return responseFactory.createResponse(code, message, null);
        } catch (Exception ex) {
            log.error("responseFactory 异常，降级返回 R", ex);
            return R.fail(code, message);
        }
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Object handleHttpMessageNotReadableException(
            HttpMessageNotReadableException e, HttpServletRequest request) {
        log.warn("HTTP 消息不可读异常: {}, uri={}",
                sanitizeForLog(e.getMessage()),
                sanitizeForLog(request.getRequestURI()));
        String code = ErrorCode.PARAM_INVALID.getCode();
        String message = "请求体格式错误";
        try {
            return responseFactory.createResponse(code, message, null);
        } catch (Exception ex) {
            log.error("responseFactory 异常，降级返回 R", ex);
            return R.fail(code, message);
        }
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Object handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        String sanitizedMethod = sanitizeForLog(e.getMethod());
        String[] supported = e.getSupportedMethods();
        String supportedMethods = supported != null ? sanitizeForLog(String.join(", ", supported)) : "";
        String message = String.format("请求方法 %s 不支持，支持的方法: %s", sanitizedMethod, supportedMethods);
        log.warn("HTTP 请求方法不支持异常: {}, uri={}",
                sanitizeForLog(message),
                sanitizeForLog(request.getRequestURI()));
        String code = ErrorCode.OPERATION_NOT_ALLOWED.getCode();
        try {
            return responseFactory.createResponse(code, message, null);
        } catch (Exception ex) {
            log.error("responseFactory 异常，降级返回 R", ex);
            return R.fail(code, message);
        }
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Object handleNoHandlerFoundException(
            NoHandlerFoundException e, HttpServletRequest request) {
        String sanitizedMethod = sanitizeForLog(e.getHttpMethod());
        String sanitizedUrl = sanitizeForLog(e.getRequestURL());
        String message = String.format("未找到请求路径: %s %s", sanitizedMethod, sanitizedUrl);
        log.warn("处理器未找到异常: {}, uri={}",
                sanitizeForLog(message),
                sanitizeForLog(request.getRequestURI()));
        String code = ErrorCode.DATA_NOT_FOUND.getCode();
        try {
            return responseFactory.createResponse(code, message, null);
        } catch (Exception ex) {
            log.error("responseFactory 异常，降级返回 R", ex);
            return R.fail(code, message);
        }
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Object handleException(Exception e, HttpServletRequest request) {
        if (e instanceof IException ie) {
            log.error("框架异常（未捕获）: code={}, message={}, uri={}",
                    sanitizeForLog(ie.getCode()),
                    sanitizeForLog(ie.getMessage()),
                    sanitizeForLog(request.getRequestURI()),
                    e);
            String code = ie.getCode();
            String message = ie.getMessage();
            try {
                return responseFactory.createResponse(code, message, null);
            } catch (Exception ex) {
                log.error("responseFactory 异常，降级返回 R", ex);
                return R.fail(code, message);
            }
        }

        log.error("未捕获的异常: uri={}",
                sanitizeForLog(request.getRequestURI()),
                e);
        String code = ErrorCode.SYSTEM_ERROR.getCode();
        String message = ErrorCode.SYSTEM_ERROR.getMessage();
        try {
            return responseFactory.createResponse(code, message, null);
        } catch (Exception ex) {
            log.error("responseFactory 异常，降级返回 R", ex);
            return R.fail(code, message);
        }
    }
}
