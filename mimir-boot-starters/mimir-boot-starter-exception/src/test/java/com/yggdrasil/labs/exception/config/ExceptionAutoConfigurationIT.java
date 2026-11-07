package com.yggdrasil.labs.exception.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import com.yggdrasil.labs.common.response.R;
import com.yggdrasil.labs.exception.handler.DefaultExceptionResponseFactory;
import com.yggdrasil.labs.exception.handler.ExceptionResponseFactory;
import com.yggdrasil.labs.exception.handler.MimirExceptionHandler;
import com.yggdrasil.labs.exception.validation.ValidationErrorData;

/**
 * 异常处理自动配置集成测试
 *
 * <p>验证 ExceptionAutoConfiguration 在 Web 环境下的 Bean 注册和覆盖行为
 *
 * @author Yggdrasil Labs
 * @since 2.1.0
 */
class ExceptionAutoConfigurationIT {

    private final WebApplicationContextRunner runner =
            new WebApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(ExceptionAutoConfiguration.class));

    @Test
    void defaultFactoryRegistered() {
        runner.run(ctx -> assertThat(ctx).hasSingleBean(DefaultExceptionResponseFactory.class));
    }

    @Test
    void handlerRegistered() {
        runner.run(ctx -> assertThat(ctx).hasSingleBean(MimirExceptionHandler.class));
    }

    @Test
    void inheritedFactoryMethodPreservesLegacyOverride() {
        new WebApplicationContextRunner()
                .withUserConfiguration(ConsumerConfiguration.class)
                .run(
                        ctx -> {
                            assertThat(ctx).hasSingleBean(MimirExceptionHandler.class);
                            assertThat(ctx.getBean(MimirExceptionHandler.class))
                                    .isInstanceOf(ConsumerHandler.class);
                            assertThat(ctx.getBean(ConsumerConfiguration.class).handlerCreations)
                                    .isEqualTo(1);
                        });
    }

    @Test
    void handlerAppliesConfiguredStructuredValidationDetails() {
        runner.withPropertyValues("mimir.boot.exception.validation.structured-details-enabled=true")
                .run(
                        ctx -> {
                            BeanPropertyBindingResult binding =
                                    new BeanPropertyBindingResult(new Object(), "form");
                            binding.addError(new FieldError("form", "name", "不能为空"));

                            ResponseEntity<?> response =
                                    (ResponseEntity<?>)
                                            ctx.getBean(MimirExceptionHandler.class)
                                                    .handleBindException(
                                                            new BindException(binding),
                                                            new MockHttpServletRequest());

                            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                            assertThat(response.getBody()).isInstanceOf(R.class);
                            assertThat(((R<?>) response.getBody()).getData())
                                    .isInstanceOf(ValidationErrorData.class);
                        });
    }

    @Test
    void additionalPropertiesBeanDoesNotPreventStartupOrReplaceBoundSettings() {
        runner.withBean("consumerExceptionProperties", ExceptionProperties.class)
                .withPropertyValues(
                        "mimir.boot.exception.validation.structured-details-enabled=true")
                .run(
                        ctx -> {
                            assertThat(ctx).hasNotFailed();
                            assertThat(ctx).hasSingleBean(MimirExceptionHandler.class);
                            BeanPropertyBindingResult binding =
                                    new BeanPropertyBindingResult(new Object(), "form");
                            binding.addError(new FieldError("form", "name", "不能为空"));
                            ResponseEntity<?> response =
                                    (ResponseEntity<?>)
                                            ctx.getBean(MimirExceptionHandler.class)
                                                    .handleBindException(
                                                            new BindException(binding),
                                                            new MockHttpServletRequest());
                            assertThat(((R<?>) response.getBody()).getData())
                                    .isInstanceOf(ValidationErrorData.class);
                        });
    }

    @Test
    void additionalPropertiesBeanStillAllowsCustomHandlerBackoff() {
        MimirExceptionHandler handler =
                new MimirExceptionHandler(new DefaultExceptionResponseFactory());
        runner.withBean("consumerExceptionProperties", ExceptionProperties.class)
                .withBean(MimirExceptionHandler.class, () -> handler)
                .run(
                        ctx -> {
                            assertThat(ctx).hasNotFailed();
                            assertThat(ctx.getBean(MimirExceptionHandler.class)).isSameAs(handler);
                        });
    }

    @Test
    void handlerPreservesReturnValueValidationServerErrorStatus() throws Exception {
        var method = String.class.getMethod("toString");
        var result =
                new ParameterValidationResult(
                        new MethodParameter(method, -1),
                        null,
                        List.of(
                                new DefaultMessageSourceResolvable(
                                        new String[] {"NotNull"}, "返回值不能为空")),
                        null,
                        null,
                        null);
        var exception =
                new HandlerMethodValidationException(
                        MethodValidationResult.create("value", method, List.of(result)));
        assertThat(exception.isForReturnValue()).isTrue();
        runner.run(
                ctx -> {
                    Object response =
                            ctx.getBean(MimirExceptionHandler.class)
                                    .handleHandlerMethodValidationException(
                                            exception, new MockHttpServletRequest());

                    assertThat(response).isInstanceOf(ResponseEntity.class);
                    assertThat(((ResponseEntity<?>) response).getStatusCode())
                            .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                });
    }

    @Test
    void customFactoryOverridesDefault() {
        runner.withBean(ExceptionResponseFactory.class, () -> (code, msg, data) -> "custom")
                .run(
                        ctx -> {
                            assertThat(ctx).doesNotHaveBean(DefaultExceptionResponseFactory.class);
                            assertThat(ctx).hasSingleBean(ExceptionResponseFactory.class);
                        });
    }

    @Test
    void beansNotRegisteredWhenDisabled() {
        runner.withPropertyValues("mimir.boot.exception.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(MimirExceptionHandler.class));
    }

    @Test
    void reactiveContextDoesNotRegisterServletExceptionComponents() {
        new ReactiveWebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ExceptionAutoConfiguration.class))
                .run(
                        ctx -> {
                            assertThat(ctx).doesNotHaveBean(MimirExceptionHandler.class);
                            assertThat(ctx).doesNotHaveBean(ExceptionResponseFactory.class);
                            assertThat(ctx).doesNotHaveBean(ExceptionProperties.class);
                        });
    }

    @Test
    void customHandlerOverridesDefault() {
        MimirExceptionHandler handler =
                new MimirExceptionHandler(new DefaultExceptionResponseFactory());
        runner.withPropertyValues("mimir.boot.exception.validation.structured-details-enabled=true")
                .withBean(MimirExceptionHandler.class, () -> handler)
                .run(
                        ctx -> {
                            assertThat(ctx).hasSingleBean(MimirExceptionHandler.class);
                            assertThat(ctx.getBean(MimirExceptionHandler.class)).isSameAs(handler);
                        });
    }

    @Test
    void componentRegistrationSupportsFactoryWithoutProperties() {
        new ApplicationContextRunner()
                .withBean(ExceptionResponseFactory.class, DefaultExceptionResponseFactory::new)
                .withBean(MimirExceptionHandler.class)
                .run(ctx -> assertThat(ctx).hasSingleBean(MimirExceptionHandler.class));
    }

    @Test
    void componentRegistrationUsesPropertiesWhenAvailable() {
        ExceptionProperties properties = new ExceptionProperties();
        properties.getValidation().setStructuredDetailsEnabled(true);
        new ApplicationContextRunner()
                .withBean(ExceptionResponseFactory.class, DefaultExceptionResponseFactory::new)
                .withBean(ExceptionProperties.class, () -> properties)
                .withBean(MimirExceptionHandler.class)
                .run(
                        ctx -> {
                            assertThat(ctx).hasSingleBean(MimirExceptionHandler.class);
                            assertThat(
                                            org.springframework.test.util.ReflectionTestUtils
                                                    .getField(
                                                            ctx.getBean(
                                                                    MimirExceptionHandler.class),
                                                            "structuredDetailsEnabled"))
                                    .isEqualTo(true);
                        });
    }

    @Configuration(proxyBeanMethods = false)
    static class ConsumerConfiguration extends ExceptionAutoConfiguration {

        private int handlerCreations;

        @Override
        public MimirExceptionHandler mimirExceptionHandler(
                ExceptionResponseFactory responseFactory) {
            handlerCreations++;
            return new ConsumerHandler(responseFactory);
        }
    }

    static class ConsumerHandler extends MimirExceptionHandler {

        ConsumerHandler(ExceptionResponseFactory responseFactory) {
            super(responseFactory);
        }
    }
}
