package com.yggdrasil.labs.exception.handler;

import com.yggdrasil.labs.common.exception.*;
import com.yggdrasil.labs.common.response.R;
import com.yggdrasil.labs.common.util.LogSanitizer;
import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
        ArrayList<String> responseErrors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.toCollection(ArrayList::new));
        ArrayList<String> logErrors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> sanitizeForLog(error.getField()) + ": " + sanitizeForLog(error.getDefaultMessage()))
                .collect(Collectors.toCollection(ArrayList::new));
        log.warn("参数校验异常: errors={}, uri={}",
                logErrors,
                sanitizeForLog(request.getRequestURI()));
        String code = ErrorCode.PARAM_INVALID.getCode();
        String message = ErrorCode.PARAM_INVALID.getMessage();
        try {
            return responseFactory.createResponse(code, message, responseErrors);
        } catch (Exception ex) {
            log.error("responseFactory 异常，降级返回 R", ex);
            return R.fail(code, message);
        }
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Object handleBindException(BindException e, HttpServletRequest request) {
        ArrayList<String> responseErrors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .collect(Collectors.toCollection(ArrayList::new));
        ArrayList<String> logErrors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> sanitizeForLog(error.getDefaultMessage()))
                .collect(Collectors.toCollection(ArrayList::new));
        log.warn("参数绑定异常: errors={}, uri={}",
                logErrors,
                sanitizeForLog(request.getRequestURI()));
        String code = ErrorCode.PARAM_INVALID.getCode();
        String message = ErrorCode.PARAM_INVALID.getMessage();
        try {
            return responseFactory.createResponse(code, message, responseErrors);
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

    @ExceptionHandler(HandlerMethodValidationException.class)
    public Object handleHandlerMethodValidationException(
            HandlerMethodValidationException e, HttpServletRequest request) {
        boolean returnValueValidation = e.isForReturnValue();
        String code = returnValueValidation ? ErrorCode.SYSTEM_ERROR.getCode() : ErrorCode.PARAM_INVALID.getCode();
        String message = returnValueValidation ? ErrorCode.SYSTEM_ERROR.getMessage() : ErrorCode.PARAM_INVALID.getMessage();
        HttpStatus status = returnValueValidation ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.BAD_REQUEST;
        log.warn("方法校验异常: returnValue={}, uri={}",
                returnValueValidation,
                sanitizeForLog(request.getRequestURI()));
        return createResponse(status, code, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Object handleConstraintViolationException(ConstraintViolationException e, HttpServletRequest request) {
        String code = ErrorCode.PARAM_INVALID.getCode();
        String message = ErrorCode.PARAM_INVALID.getMessage();
        log.warn("约束校验异常: violations={}, uri={}",
                e.getConstraintViolations().size(),
                sanitizeForLog(request.getRequestURI()));
        return createResponse(HttpStatus.BAD_REQUEST, code, message);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public Object handleMissingRequestHeaderException(MissingRequestHeaderException e, HttpServletRequest request) {
        String headerName = sanitizeForLog(e.getHeaderName());
        String code = ErrorCode.PARAM_MISSING.getCode();
        String message = String.format("缺少必需请求头: %s", headerName);
        log.warn("缺少请求头异常: header={}, uri={}", headerName, sanitizeForLog(request.getRequestURI()));
        return createResponse(HttpStatus.BAD_REQUEST, code, message);
    }

    @ExceptionHandler(MissingPathVariableException.class)
    public Object handleMissingPathVariableException(MissingPathVariableException e, HttpServletRequest request) {
        String code = ErrorCode.SYSTEM_ERROR.getCode();
        String message = ErrorCode.SYSTEM_ERROR.getMessage();
        log.error("缺少路径变量异常: variable={}, uri={}",
                sanitizeForLog(e.getVariableName()),
                sanitizeForLog(request.getRequestURI()));
        return createResponse(HttpStatus.INTERNAL_SERVER_ERROR, code, message);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public Object handleHttpMediaTypeNotAcceptableException(
            HttpMediaTypeNotAcceptableException e, HttpServletRequest request) {
        String code = ErrorCode.OPERATION_NOT_ALLOWED.getCode();
        String message = ErrorCode.OPERATION_NOT_ALLOWED.getMessage();
        log.warn("不可接受的响应媒体类型: uri={}", sanitizeForLog(request.getRequestURI()));
        return createResponse(HttpStatus.NOT_ACCEPTABLE, code, message);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public Object handleHttpMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException e, HttpServletRequest request) {
        String code = ErrorCode.OPERATION_NOT_ALLOWED.getCode();
        String message = ErrorCode.OPERATION_NOT_ALLOWED.getMessage();
        log.warn("不支持的请求媒体类型: uri={}", sanitizeForLog(request.getRequestURI()));
        return createResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, code, message);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Object handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e, HttpServletRequest request) {
        String code = ErrorCode.PARAM_INVALID.getCode();
        String message = ErrorCode.PARAM_INVALID.getMessage();
        log.warn("上传大小超限: uri={}", sanitizeForLog(request.getRequestURI()));
        return createResponse(HttpStatus.PAYLOAD_TOO_LARGE, code, message);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Object handleNoResourceFoundException(NoResourceFoundException e, HttpServletRequest request) {
        String code = ErrorCode.DATA_NOT_FOUND.getCode();
        String message = ErrorCode.DATA_NOT_FOUND.getMessage();
        log.warn("静态资源未找到: path={}, uri={}",
                sanitizeForLog(e.getResourcePath()),
                sanitizeForLog(request.getRequestURI()));
        return createResponse(HttpStatus.NOT_FOUND, code, message);
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

    private ResponseEntity<Object> createResponse(HttpStatus status, String code, String message) {
        try {
            Object response = responseFactory.createResponse(code, message, null);
            if (response instanceof ResponseEntity<?> responseEntity) {
                return ResponseEntity.status(status)
                        .headers(responseEntity.getHeaders())
                        .body(responseEntity.getBody());
            }
            return ResponseEntity.status(status).body(response);
        } catch (Exception ex) {
            log.error("responseFactory 异常，降级返回 R", ex);
            return ResponseEntity.status(status).body(R.fail(code, message));
        }
    }
}
