package com.yggdrasil.labs.exception.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;

import com.yggdrasil.labs.exception.config.ExceptionProperties;
import com.yggdrasil.labs.exception.validation.ValidationErrorData;

class ValidationHandlerTest {
    private final HttpServletRequest request = mock(HttpServletRequest.class);

    @Test
    void directOldConstructorKeepsLegacyDataAndNewConstructorUsesProperties() {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Form(), "form");
        binding.rejectValue("name", "NotBlank", "原消息");
        BindException exception = new BindException(binding);
        ExceptionResponseFactory factory = mock(ExceptionResponseFactory.class);
        when(factory.createResponse(anyString(), anyString(), any())).thenReturn("body");

        new LegacySubclass(factory).handleBindException(exception, request);
        verify(factory).createResponse(anyString(), anyString(), eq(List.of("原消息")));

        reset(factory);
        ExceptionProperties properties = new ExceptionProperties();
        properties.getValidation().setStructuredDetailsEnabled(true);
        new ConfiguredSubclass(factory, properties).handleBindException(exception, request);
        verify(factory).createResponse(anyString(), anyString(), isA(ValidationErrorData.class));
    }

    @Test
    void extractionFailureKeepsInputStatusAndCallsFactoryOnceWithNullData() {
        BindingResult binding = mock(BindingResult.class);
        when(binding.getAllErrors()).thenThrow(new IllegalStateException("secret-value"));
        ExceptionProperties properties = new ExceptionProperties();
        properties.getValidation().setStructuredDetailsEnabled(true);
        ExceptionResponseFactory factory = mock(ExceptionResponseFactory.class);
        MimirExceptionHandler handler = new MimirExceptionHandler(factory, properties);

        ResponseEntity<?> response =
                (ResponseEntity<?>)
                        handler.handleBindException(new BindException(binding), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(factory, times(1)).createResponse(eq("00004"), anyString(), isNull());
    }

    @Test
    void classificationFailureUsesServerErrorAndCallsFactoryOnce() {
        ConstraintViolationException exception = mock(ConstraintViolationException.class);
        when(exception.getConstraintViolations())
                .thenThrow(new IllegalStateException("secret-value"));
        ExceptionResponseFactory factory = mock(ExceptionResponseFactory.class);

        ResponseEntity<?> response =
                (ResponseEntity<?>)
                        new MimirExceptionHandler(factory)
                                .handleConstraintViolationException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        verify(factory, times(1)).createResponse(eq("10000"), eq("系统错误"), isNull());
    }

    static class Form {
        public String getName() {
            return "";
        }
    }

    static class LegacySubclass extends MimirExceptionHandler {
        LegacySubclass(ExceptionResponseFactory factory) {
            super(factory);
        }
    }

    static class ConfiguredSubclass extends MimirExceptionHandler {
        ConfiguredSubclass(ExceptionResponseFactory factory, ExceptionProperties properties) {
            super(factory, properties);
        }
    }
}
