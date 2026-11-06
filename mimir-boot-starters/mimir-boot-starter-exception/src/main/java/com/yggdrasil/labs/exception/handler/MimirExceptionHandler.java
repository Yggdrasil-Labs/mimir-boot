package com.yggdrasil.labs.exception.handler;

import java.util.ArrayList;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.yggdrasil.labs.common.exception.*;
import com.yggdrasil.labs.common.response.R;
import com.yggdrasil.labs.common.util.LogSanitizer;
import com.yggdrasil.labs.exception.config.ExceptionProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Mimir 全局异常处理器
 *
 * <p>统一处理应用程序中的所有异常，通过 {@link ExceptionResponseFactory} 构建响应体。
 *
 * @author Yggdrasil Labs
 * @since 2.1.0
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class MimirExceptionHandler {

    private final ExceptionResponseFactory responseFactory;
    private final boolean structuredDetailsEnabled;

    @Autowired(required = false)
    public MimirExceptionHandler(ExceptionResponseFactory responseFactory) {
        this(responseFactory, new ExceptionProperties());
    }

    @Autowired(required = false)
    public MimirExceptionHandler(
            ExceptionResponseFactory responseFactory, ExceptionProperties properties) {
        this.responseFactory = responseFactory;
        this.structuredDetailsEnabled = properties.getValidation().isStructuredDetailsEnabled();
    }

    private String sanitizeForLog(String input) {
        return LogSanitizer.sanitize(input);
    }

    /**
     * 处理业务异常，沿用 HTTP 200 与异常声明的业务码、消息。
     *
     * @param e 触发处理的异常
     * @param request 当前 HTTP 请求，用于记录请求路径
     * @return 包含 HTTP 状态及统一响应体的结果
     */
    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.OK)
    public Object handleBizException(BizException e, HttpServletRequest request) {
        log.warn(
                "业务异常: code={}, message={}, uri={}",
                sanitizeForLog(e.getCode()),
                sanitizeForLog(e.getMessage()),
                sanitizeForLog(request.getRequestURI()));
        String code = e.getCode();
        String message = e.getMessage();
        return createResponse(HttpStatus.OK, e, code, message, null);
    }

    /**
     * 处理系统异常，返回 HTTP 500 与异常声明的错误信息。
     *
     * @param e 触发处理的异常
     * @param request 当前 HTTP 请求，用于记录请求路径
     * @return 包含 HTTP 状态及统一响应体的结果
     */
    @ExceptionHandler(SystemException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Object handleSystemException(SystemException e, HttpServletRequest request) {
        log.error(
                "系统异常: code={}, message={}, uri={}, type={}",
                sanitizeForLog(e.getCode()),
                sanitizeForLog(e.getMessage()),
                sanitizeForLog(request.getRequestURI()),
                e.getClass().getSimpleName());
        String code = e.getCode();
        String message = e.getMessage();
        return createResponse(HttpStatus.INTERNAL_SERVER_ERROR, e, code, message, null);
    }

    /**
     * 处理其他框架异常，返回 HTTP 500 与异常声明的错误信息。
     *
     * @param e 触发处理的异常
     * @param request 当前 HTTP 请求，用于记录请求路径
     * @return 包含 HTTP 状态及统一响应体的结果
     */
    @ExceptionHandler(BaseException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Object handleBaseException(BaseException e, HttpServletRequest request) {
        log.error(
                "框架异常: code={}, message={}, uri={}, type={}",
                sanitizeForLog(e.getCode()),
                sanitizeForLog(e.getMessage()),
                sanitizeForLog(request.getRequestURI()),
                e.getClass().getSimpleName());
        String code = e.getCode();
        String message = e.getMessage();
        return createResponse(HttpStatus.INTERNAL_SERVER_ERROR, e, code, message, null);
    }

    /**
     * 处理请求体校验错误，按配置返回旧格式或安全的结构化明细。
     *
     * @param e 触发处理的异常
     * @param request 当前 HTTP 请求，用于记录请求路径
     * @return 包含 HTTP 状态及统一响应体的结果
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Object handleMethodArgumentNotValidException(
            MethodArgumentNotValidException e, HttpServletRequest request) {
        if (structuredDetailsEnabled) {
            Object data =
                    validationData(
                            () ->
                                    ValidationErrorExtractor.extract(
                                            e.getBindingResult(), e.getParameter()));
            log.warn("参数校验异常: uri={}", sanitizeForLog(request.getRequestURI()));
            return createResponse(
                    HttpStatus.BAD_REQUEST,
                    e,
                    ErrorCode.PARAM_INVALID.getCode(),
                    ErrorCode.PARAM_INVALID.getMessage(),
                    data);
        }
        ArrayList<String> responseErrors =
                e.getBindingResult().getFieldErrors().stream()
                        .map(error -> error.getField() + ": " + error.getDefaultMessage())
                        .collect(Collectors.toCollection(ArrayList::new));
        ArrayList<String> logErrors =
                e.getBindingResult().getFieldErrors().stream()
                        .map(
                                error ->
                                        sanitizeForLog(error.getField())
                                                + ": "
                                                + sanitizeForLog(error.getDefaultMessage()))
                        .collect(Collectors.toCollection(ArrayList::new));
        log.warn("参数校验异常: errors={}, uri={}", logErrors, sanitizeForLog(request.getRequestURI()));
        String code = ErrorCode.PARAM_INVALID.getCode();
        String message = ErrorCode.PARAM_INVALID.getMessage();
        return createResponse(HttpStatus.BAD_REQUEST, e, code, message, responseErrors);
    }

    /**
     * 处理参数绑定错误，保留旧格式响应与结构化明细开关的语义。
     *
     * @param e 触发处理的异常
     * @param request 当前 HTTP 请求，用于记录请求路径
     * @return 包含 HTTP 状态及统一响应体的结果
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Object handleBindException(BindException e, HttpServletRequest request) {
        if (structuredDetailsEnabled) {
            Object data =
                    validationData(
                            () ->
                                    ValidationErrorExtractor.extract(
                                            e.getBindingResult(), (Integer) null));
            log.warn("参数绑定异常: uri={}", sanitizeForLog(request.getRequestURI()));
            return createResponse(
                    HttpStatus.BAD_REQUEST,
                    e,
                    ErrorCode.PARAM_INVALID.getCode(),
                    ErrorCode.PARAM_INVALID.getMessage(),
                    data);
        }
        ArrayList<String> responseErrors =
                e.getBindingResult().getFieldErrors().stream()
                        .map(error -> error.getDefaultMessage())
                        .collect(Collectors.toCollection(ArrayList::new));
        ArrayList<String> logErrors =
                e.getBindingResult().getFieldErrors().stream()
                        .map(error -> sanitizeForLog(error.getDefaultMessage()))
                        .collect(Collectors.toCollection(ArrayList::new));
        log.warn("参数绑定异常: errors={}, uri={}", logErrors, sanitizeForLog(request.getRequestURI()));
        String code = ErrorCode.PARAM_INVALID.getCode();
        String message = ErrorCode.PARAM_INVALID.getMessage();
        return createResponse(HttpStatus.BAD_REQUEST, e, code, message, responseErrors);
    }

    /**
     * 处理必需请求参数缺失，返回 HTTP 400 与缺失参数提示。
     *
     * @param e 触发处理的异常
     * @param request 当前 HTTP 请求，用于记录请求路径
     * @return 包含 HTTP 状态及统一响应体的结果
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Object handleMissingServletRequestParameterException(
            MissingServletRequestParameterException e, HttpServletRequest request) {
        String sanitizedParamName = sanitizeForLog(e.getParameterName());
        String message = String.format("缺少必需参数: %s", sanitizedParamName);
        log.warn(
                "缺少请求参数异常: {}, uri={}",
                sanitizeForLog(message),
                sanitizeForLog(request.getRequestURI()));
        String code = ErrorCode.PARAM_MISSING.getCode();
        return createResponse(HttpStatus.BAD_REQUEST, e, code, message, null);
    }

    /**
     * 处理方法校验错误，按输入、输出来源确定状态与明细可见性。
     *
     * @param e 触发处理的异常
     * @param request 当前 HTTP 请求，用于记录请求路径
     * @return 包含 HTTP 状态及统一响应体的结果
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public Object handleHandlerMethodValidationException(
            HandlerMethodValidationException e, HttpServletRequest request) {
        ValidationErrorExtractor.Origin origin =
                validationOrigin(() -> ValidationErrorExtractor.classify(e));
        Object data =
                origin == ValidationErrorExtractor.Origin.INPUT && structuredDetailsEnabled
                        ? validationData(() -> ValidationErrorExtractor.extract(e))
                        : null;
        log.warn("方法校验异常: origin={}, uri={}", origin, sanitizeForLog(request.getRequestURI()));
        return validationResponse(e, origin, data);
    }

    /**
     * 处理约束校验错误，仅对可确认的输入错误返回参数错误响应。
     *
     * @param e 触发处理的异常
     * @param request 当前 HTTP 请求，用于记录请求路径
     * @return 包含 HTTP 状态及统一响应体的结果
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Object handleConstraintViolationException(
            ConstraintViolationException e, HttpServletRequest request) {
        ValidationErrorExtractor.Origin origin =
                validationOrigin(() -> ValidationErrorExtractor.classify(e));
        Object data =
                origin == ValidationErrorExtractor.Origin.INPUT && structuredDetailsEnabled
                        ? validationData(() -> ValidationErrorExtractor.extract(e))
                        : null;
        log.warn("约束校验异常: origin={}, uri={}", origin, sanitizeForLog(request.getRequestURI()));
        return validationResponse(e, origin, data);
    }

    private Object validationResponse(
            Exception e, ValidationErrorExtractor.Origin origin, Object data) {
        boolean input = origin == ValidationErrorExtractor.Origin.INPUT;
        ErrorCode error = input ? ErrorCode.PARAM_INVALID : ErrorCode.SYSTEM_ERROR;
        return createResponse(
                input ? HttpStatus.BAD_REQUEST : HttpStatus.INTERNAL_SERVER_ERROR,
                e,
                error.getCode(),
                error.getMessage(),
                input ? data : null);
    }

    private ValidationErrorExtractor.Origin validationOrigin(
            Supplier<ValidationErrorExtractor.Origin> classifier) {
        try {
            return classifier.get();
        } catch (RuntimeException ex) {
            log.warn("无法判断校验异常来源: type={}", ex.getClass().getName());
            return ValidationErrorExtractor.Origin.UNKNOWN;
        }
    }

    private Object validationData(Supplier<Object> extractor) {
        try {
            return extractor.get();
        } catch (RuntimeException ex) {
            log.warn("无法提取校验明细: type={}", ex.getClass().getName());
            return null;
        }
    }

    /**
     * 处理必需请求头缺失，返回 HTTP 400 与脱敏后的头名称。
     *
     * @param e 触发处理的异常
     * @param request 当前 HTTP 请求，用于记录请求路径
     * @return 包含 HTTP 状态及统一响应体的结果
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public Object handleMissingRequestHeaderException(
            MissingRequestHeaderException e, HttpServletRequest request) {
        String headerName = sanitizeForLog(e.getHeaderName());
        String code = ErrorCode.PARAM_MISSING.getCode();
        String message = String.format("缺少必需请求头: %s", headerName);
        log.warn("缺少请求头异常: header={}, uri={}", headerName, sanitizeForLog(request.getRequestURI()));
        return createResponse(HttpStatus.BAD_REQUEST, e, code, message, null);
    }

    /**
     * 处理服务端声明的路径变量缺失，返回通用系统错误响应。
     *
     * @param e 触发处理的异常
     * @param request 当前 HTTP 请求，用于记录请求路径
     * @return 包含 HTTP 状态及统一响应体的结果
     */
    @ExceptionHandler(MissingPathVariableException.class)
    public Object handleMissingPathVariableException(
            MissingPathVariableException e, HttpServletRequest request) {
        String code = ErrorCode.SYSTEM_ERROR.getCode();
        String message = ErrorCode.SYSTEM_ERROR.getMessage();
        log.error(
                "缺少路径变量异常: variable={}, uri={}",
                sanitizeForLog(e.getVariableName()),
                sanitizeForLog(request.getRequestURI()));
        return createResponse(HttpStatus.INTERNAL_SERVER_ERROR, e, code, message, null);
    }

    /**
     * 处理客户端不接受响应媒体类型的情况，返回 HTTP 406。
     *
     * @param e 触发处理的异常
     * @param request 当前 HTTP 请求，用于记录请求路径
     * @return 包含 HTTP 状态及统一响应体的结果
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public Object handleHttpMediaTypeNotAcceptableException(
            HttpMediaTypeNotAcceptableException e, HttpServletRequest request) {
        String code = ErrorCode.OPERATION_NOT_ALLOWED.getCode();
        String message = ErrorCode.OPERATION_NOT_ALLOWED.getMessage();
        log.warn("不可接受的响应媒体类型: uri={}", sanitizeForLog(request.getRequestURI()));
        return createResponse(HttpStatus.NOT_ACCEPTABLE, e, code, message, null);
    }

    /**
     * 处理请求媒体类型不受支持的情况，返回 HTTP 415。
     *
     * @param e 触发处理的异常
     * @param request 当前 HTTP 请求，用于记录请求路径
     * @return 包含 HTTP 状态及统一响应体的结果
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public Object handleHttpMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException e, HttpServletRequest request) {
        String code = ErrorCode.OPERATION_NOT_ALLOWED.getCode();
        String message = ErrorCode.OPERATION_NOT_ALLOWED.getMessage();
        log.warn("不支持的请求媒体类型: uri={}", sanitizeForLog(request.getRequestURI()));
        return createResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, e, code, message, null);
    }

    /**
     * 处理上传大小超限，返回 HTTP 413 与通用参数错误信息。
     *
     * @param e 触发处理的异常
     * @param request 当前 HTTP 请求，用于记录请求路径
     * @return 包含 HTTP 状态及统一响应体的结果
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Object handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException e, HttpServletRequest request) {
        String code = ErrorCode.PARAM_INVALID.getCode();
        String message = ErrorCode.PARAM_INVALID.getMessage();
        log.warn("上传大小超限: uri={}", sanitizeForLog(request.getRequestURI()));
        return createResponse(HttpStatus.PAYLOAD_TOO_LARGE, e, code, message, null);
    }

    /**
     * 处理静态资源缺失，返回 HTTP 404 与通用资源未找到信息。
     *
     * @param e 触发处理的异常
     * @param request 当前 HTTP 请求，用于记录请求路径
     * @return 包含 HTTP 状态及统一响应体的结果
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public Object handleNoResourceFoundException(
            NoResourceFoundException e, HttpServletRequest request) {
        String code = ErrorCode.DATA_NOT_FOUND.getCode();
        String message = ErrorCode.DATA_NOT_FOUND.getMessage();
        log.warn(
                "静态资源未找到: path={}, uri={}",
                sanitizeForLog(e.getResourcePath()),
                sanitizeForLog(request.getRequestURI()));
        return createResponse(HttpStatus.NOT_FOUND, e, code, message, null);
    }

    /**
     * 处理参数类型转换失败，返回 HTTP 400 与参数声明类型提示。
     *
     * @param e 触发处理的异常
     * @param request 当前 HTTP 请求，用于记录请求路径
     * @return 包含 HTTP 状态及统一响应体的结果
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Object handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        String sanitizedParamName = sanitizeForLog(e.getName());
        String expectedType =
                e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "unknown";
        String message = String.format("参数类型不匹配: %s，期望类型: %s", sanitizedParamName, expectedType);
        log.warn(
                "参数类型不匹配异常: {}, uri={}",
                sanitizeForLog(message),
                sanitizeForLog(request.getRequestURI()));
        String code = ErrorCode.PARAM_INVALID.getCode();
        return createResponse(HttpStatus.BAD_REQUEST, e, code, message, null);
    }

    /**
     * 处理请求体读取失败，仅记录异常类型与可用的 JSON 行列位置。
     *
     * @param e 触发处理的异常
     * @param request 当前 HTTP 请求，用于记录请求路径
     * @return 包含 HTTP 状态及统一响应体的结果
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Object handleHttpMessageNotReadableException(
            HttpMessageNotReadableException e, HttpServletRequest request) {
        String uri = sanitizeForLog(request.getRequestURI());
        String exceptionType = e.getClass().getSimpleName();
        JsonLocation location =
                e.getCause() instanceof JsonProcessingException jsonException
                        ? jsonException.getLocation()
                        : null;
        if (location == null) {
            log.warn("HTTP 消息不可读异常: type={}, uri={}", exceptionType, uri);
        } else {
            log.warn(
                    "HTTP 消息不可读异常: type={}, line={}, column={}, uri={}",
                    exceptionType,
                    location.getLineNr(),
                    location.getColumnNr(),
                    uri);
        }
        String code = ErrorCode.PARAM_INVALID.getCode();
        String message = "请求体格式错误";
        return createResponse(HttpStatus.BAD_REQUEST, e, code, message, null);
    }

    /**
     * 处理请求方法不受支持的情况，返回 HTTP 405 与支持方法信息。
     *
     * @param e 触发处理的异常
     * @param request 当前 HTTP 请求，用于记录请求路径
     * @return 包含 HTTP 状态及统一响应体的结果
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Object handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        String sanitizedMethod = sanitizeForLog(e.getMethod());
        String[] supported = e.getSupportedMethods();
        String supportedMethods =
                supported != null ? sanitizeForLog(String.join(", ", supported)) : "";
        String message = String.format("请求方法 %s 不支持，支持的方法: %s", sanitizedMethod, supportedMethods);
        log.warn(
                "HTTP 请求方法不支持异常: {}, uri={}",
                sanitizeForLog(message),
                sanitizeForLog(request.getRequestURI()));
        String code = ErrorCode.OPERATION_NOT_ALLOWED.getCode();
        return createResponse(HttpStatus.METHOD_NOT_ALLOWED, e, code, message, null);
    }

    /**
     * 处理未匹配到请求处理器的情况，返回 HTTP 404 与脱敏后的请求位置。
     *
     * @param e 触发处理的异常
     * @param request 当前 HTTP 请求，用于记录请求路径
     * @return 包含 HTTP 状态及统一响应体的结果
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Object handleNoHandlerFoundException(
            NoHandlerFoundException e, HttpServletRequest request) {
        String sanitizedMethod = sanitizeForLog(e.getHttpMethod());
        String sanitizedUrl = sanitizeForLog(e.getRequestURL());
        String message = String.format("未找到请求路径: %s %s", sanitizedMethod, sanitizedUrl);
        log.warn(
                "处理器未找到异常: {}, uri={}",
                sanitizeForLog(message),
                sanitizeForLog(request.getRequestURI()));
        String code = ErrorCode.DATA_NOT_FOUND.getCode();
        return createResponse(HttpStatus.NOT_FOUND, e, code, message, null);
    }

    /**
     * 兜底处理异常，保留框架业务语义与标准响应状态注解的解析规则。
     *
     * @param e 触发处理的异常
     * @param request 当前 HTTP 请求，用于记录请求路径
     * @return 包含 HTTP 状态及统一响应体的结果
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Object handleException(Exception e, HttpServletRequest request) {
        // 直接调用兜底入口时也不能让框架异常子类的注解覆盖固定状态。
        HttpStatusCode status = HttpStatus.INTERNAL_SERVER_ERROR;
        if (e instanceof BizException) {
            status = HttpStatus.OK;
        } else if (!(e instanceof BaseException)) {
            ResponseStatus annotation =
                    AnnotatedElementUtils.findMergedAnnotation(e.getClass(), ResponseStatus.class);
            if (annotation != null) {
                status = annotation.code();
            }
        }
        if (e instanceof IException ie) {
            log.error(
                    "框架异常（未捕获）: code={}, message={}, uri={}, type={}",
                    sanitizeForLog(ie.getCode()),
                    sanitizeForLog(ie.getMessage()),
                    sanitizeForLog(request.getRequestURI()),
                    e.getClass().getSimpleName());
            String code = ie.getCode();
            String message = ie.getMessage();
            return createResponse(
                    new ExceptionHttpMetadata(status, HttpHeaders.EMPTY, code, message), null);
        }

        log.error("未捕获的异常: uri={}", sanitizeForLog(request.getRequestURI()), e);
        ErrorCode error = errorForStatus(status);
        return createResponse(
                new ExceptionHttpMetadata(
                        status, HttpHeaders.EMPTY, error.getCode(), error.getMessage()),
                null);
    }

    /**
     * 处理标准响应状态异常，保留 HTTP 状态与协议响应头。
     *
     * @param e 触发处理的异常
     * @param request 当前 HTTP 请求，用于记录请求路径
     * @return 包含 HTTP 状态及统一响应体的结果
     */
    @ExceptionHandler(ResponseStatusException.class)
    public Object handleResponseStatusException(
            ResponseStatusException e, HttpServletRequest request) {
        return handleStandardHttpException(e, request);
    }

    /**
     * 处理标准错误响应异常，保留 HTTP 状态与协议响应头。
     *
     * @param e 触发处理的异常
     * @param request 当前 HTTP 请求，用于记录请求路径
     * @return 包含 HTTP 状态及统一响应体的结果
     */
    @ExceptionHandler(ErrorResponseException.class)
    public Object handleErrorResponseException(
            ErrorResponseException e, HttpServletRequest request) {
        return handleStandardHttpException(e, request);
    }

    private Object handleStandardHttpException(
            ErrorResponseException e, HttpServletRequest request) {
        HttpStatusCode status = e.getStatusCode();
        ErrorCode error = errorForStatus(status);
        String code = e instanceof IException ie ? ie.getCode() : error.getCode();
        String message = e instanceof IException ie ? ie.getMessage() : error.getMessage();
        log.warn(
                "HTTP 状态异常: status={}, type={}, uri={}",
                status.value(),
                e.getClass().getSimpleName(),
                sanitizeForLog(request.getRequestURI()));
        return createResponse(status, e, code, message, null);
    }

    private static ErrorCode errorForStatus(HttpStatusCode status) {
        return switch (status.value()) {
            case 401 -> ErrorCode.UNAUTHORIZED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.DATA_NOT_FOUND;
            case 405, 406, 415 -> ErrorCode.OPERATION_NOT_ALLOWED;
            case 413 -> ErrorCode.PARAM_INVALID;
            default ->
                    status.is4xxClientError()
                            ? ErrorCode.PARAM_ERROR
                            : status.is5xxServerError() ? ErrorCode.SYSTEM_ERROR : ErrorCode.FAIL;
        };
    }

    private ResponseEntity<Object> createResponse(
            HttpStatusCode status, Exception exception, String code, String message, Object data) {
        HttpHeaders headers =
                exception instanceof ErrorResponse error ? error.getHeaders() : HttpHeaders.EMPTY;
        return createResponse(new ExceptionHttpMetadata(status, headers, code, message), data);
    }

    private ResponseEntity<Object> createResponse(ExceptionHttpMetadata metadata, Object data) {
        // 无实体状态不调用工厂构造响应体；其他响应交给原生 MVC 完成写出。
        if (metadata.forbidsBody()) {
            return new ResponseEntity<>(null, metadata.headers(), metadata.status());
        }
        Object response;
        try {
            response = responseFactory.createResponse(metadata.code(), metadata.message(), data);
        } catch (Exception ex) {
            // 不重试业务工厂，保留已解析的状态和协议头并直接使用默认响应。
            log.error("responseFactory 异常，降级返回 R", ex);
            return new ResponseEntity<>(
                    R.fail(metadata.code(), metadata.message()),
                    metadata.headers(),
                    metadata.status());
        }
        if (response instanceof ResponseEntity<?> entity) {
            // 工厂可以提供响应体和合法响应头，但不能覆盖处理器确定的状态。
            return new ResponseEntity<>(
                    entity.getBody(),
                    metadata.mergeHeaders(entity.getHeaders()),
                    metadata.status());
        }
        return new ResponseEntity<>(response, metadata.headers(), metadata.status());
    }
}
