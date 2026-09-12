package com.yggdrasil.labs.web.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.yggdrasil.labs.common.response.R;
import com.yggdrasil.labs.common.constant.HttpHeaderConstants;
import com.yggdrasil.labs.web.advice.ResponseBodyEnhancer;
import com.yggdrasil.labs.web.interceptor.TraceInterceptor;
import com.yggdrasil.labs.web.interceptor.WebInterceptor;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.servlet.handler.HandlerExceptionResolverComposite;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web 层自动配置集成测试
 *
 * <p>验证 WebAutoConfiguration 在 Servlet Web 环境下的 Bean 注册行为</p>
 *
 * @author Yggdrasil Labs
 * @since 2.1.0
 */
class WebAutoConfigurationIT {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WebAutoConfiguration.class));

    @Test
    void disabledResponseEnhancementPreservesPrimaryApplicationRegistrations() {
        runner.withConfiguration(AutoConfigurations.of(WebMvcAutoConfiguration.class, JacksonAutoConfiguration.class))
                .withPropertyValues("mimir.boot.web.response.enabled=false")
                .withBean("applicationWebMvcRegistrations", WebMvcRegistrations.class,
                        () -> new WebMvcRegistrations() {}, definition -> definition.setPrimary(true))
                .withBean(DisabledResponseController.class, DisabledResponseController::new)
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    MockMvcBuilders.webAppContextSetup(ctx.getSourceApplicationContext()).build()
                            .perform(get("/disabled-response").header(HttpHeaderConstants.TRACE_ID_HEADER, "disabled-trace"))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.data").value("payload"))
                            .andExpect(jsonPath("$.traceId").doesNotExist());
                });
    }

    @Test
    void traceInterceptorRegistered() {
        runner.run(ctx -> assertThat(ctx).hasSingleBean(TraceInterceptor.class));
    }

    @Test
    void customTraceInterceptorOverridesDefaultRegardlessOfBeanName() {
        runner.withBean("applicationTraceInterceptor", TraceInterceptor.class, TraceInterceptor::new)
                .run(ctx -> assertThat(ctx).hasSingleBean(TraceInterceptor.class));
    }

    @Test
    void corsConfigRegistered() {
        runner.withPropertyValues(
                        "mimir.boot.web.cors.enabled=true",
                        "mimir.boot.web.cors.allowed-origins[0]=https://app.example.com"
                )
                .run(ctx -> assertThat(ctx).hasSingleBean(CorsConfig.class));
    }

    @Test
    void responseBodyEnhancerRegistered() {
        runner.run(ctx -> assertThat(ctx).hasSingleBean(ResponseBodyEnhancer.class));
    }

    @Test
    void customResponseBodyEnhancerOverridesDefault() {
        runner.withBean(ResponseBodyEnhancer.class, () -> new ResponseBodyEnhancer(new WebProperties()))
                .run(ctx -> assertThat(ctx).hasSingleBean(ResponseBodyEnhancer.class));
    }

    @Test
    void responseAdviceDoesNotIntroduceWebMvcRegistrations() {
        runner.run(ctx -> {
            assertThat(ctx).doesNotHaveBean(WebMvcRegistrations.class);
            assertThat(ctx).hasSingleBean(ResponseBodyEnhancerAdviceFactory.MvcResponseBodyEnhancer.class);
        });
    }

    @Test
    void applicationWebMvcRegistrationsRemainsTheOnlyRegistration() {
        WebMvcRegistrations application = new WebMvcRegistrations() {};
        runner.withBean("applicationWebMvcRegistrations", WebMvcRegistrations.class, () -> application)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(WebMvcRegistrations.class);
                    assertThat(ctx.getBean(WebMvcRegistrations.class)).isSameAs(application);
                });
    }

    @Test
    void requestMappingHandlerMappingFromApplicationWebMvcRegistrationsIsPreserved() {
        RequestMappingHandlerMapping applicationMapping = new RequestMappingHandlerMapping();
        runner.withConfiguration(AutoConfigurations.of(WebMvcAutoConfiguration.class, JacksonAutoConfiguration.class))
                .withBean(WebMvcRegistrations.class, () -> new WebMvcRegistrations() {
                    @Override
                    public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
                        return applicationMapping;
                    }
                })
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx.getBean("requestMappingHandlerMapping")).isSameAs(applicationMapping);
                });
    }

    @Test
    void exceptionResolverPreservesExplicitReturnValueHandlers() {
        HandlerMethodReturnValueHandler customReturnValueHandler = new HandlerMethodReturnValueHandler() {
            @Override
            public boolean supportsReturnType(MethodParameter returnType) {
                return false;
            }

            @Override
            public void handleReturnValue(
                    Object returnValue,
                    MethodParameter returnType,
                    ModelAndViewContainer modelAndViewContainer,
                    NativeWebRequest webRequest) {
                // 用于验证完整自定义返回值处理器列表不会被清空。
            }
        };
        ExceptionHandlerExceptionResolver applicationResolver = new ExceptionHandlerExceptionResolver();
        applicationResolver.setReturnValueHandlers(List.of(customReturnValueHandler));
        runner.withConfiguration(AutoConfigurations.of(WebMvcAutoConfiguration.class, JacksonAutoConfiguration.class))
                .withBean(WebMvcRegistrations.class, () -> new WebMvcRegistrations() {
                    @Override
                    public ExceptionHandlerExceptionResolver getExceptionHandlerExceptionResolver() {
                        return applicationResolver;
                    }
                })
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    HandlerExceptionResolverComposite composite =
                            ctx.getBean("handlerExceptionResolver", HandlerExceptionResolverComposite.class);
                    assertThat(composite.getExceptionResolvers()).contains(applicationResolver);
                    assertThat(applicationResolver.getReturnValueHandlers().getHandlers())
                            .containsExactly(customReturnValueHandler);
                });
    }

    @Test
    void internalAdviceCannotBeIndependentlyComponentScanned() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(ControllerAdvice.class));
        assertThat(scanner.findCandidateComponents("com.yggdrasil.labs.web"))
                .noneMatch(bean -> ResponseBodyEnhancerAdviceFactory.MvcResponseBodyEnhancer.class.getName()
                        .equals(bean.getBeanClassName()));
    }

    @Test
    void componentScanDoesNotBypassDisabledWebAutoConfiguration() {
        runner.withPropertyValues("mimir.boot.web.enabled=false")
                .withUserConfiguration(WideComponentScanConfiguration.class)
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).doesNotHaveBean(ResponseBodyEnhancer.class);
                    assertThat(ctx).doesNotHaveBean(ResponseBodyAdvice.class);
                });
    }

    @Test
    void webPropertiesRegistered() {
        runner.run(ctx -> assertThat(ctx).hasSingleBean(WebProperties.class));
    }

    @Test
    void webInterceptorRegistered() {
        runner.run(ctx -> assertThat(ctx).hasSingleBean(WebInterceptor.class));
    }

    @Test
    void customWebInterceptorOverridesDefault() {
        runner.withBean(WebInterceptor.class, WebInterceptor::new)
                .run(ctx -> assertThat(ctx).hasSingleBean(WebInterceptor.class));
    }

    @Test
    void webMvcConfigRegistered() {
        runner.run(ctx -> assertThat(ctx).hasSingleBean(WebMvcConfig.class));
    }

    @Test
    void jacksonConfigRegistered() {
        runner.run(ctx -> assertThat(ctx).hasSingleBean(JacksonConfig.class));
    }

    @Test
    void preservesConsumerJacksonModuleAlongsideStarterDateTimeFormat() {
        runner.withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                .withBean(SimpleModule.class, () -> new SimpleModule()
                        .addSerializer(ConsumerValue.class, new ConsumerValueSerializer()))
                .run(ctx -> {
                    ObjectMapper objectMapper = ctx.getBean(ObjectMapper.class);

                    assertThat(objectMapper.writeValueAsString(new ConsumerValue())).isEqualTo("\"consumer-module\"");
                    assertThat(objectMapper.writeValueAsString(LocalDateTime.of(2026, 8, 12, 9, 30, 0)))
                            .isEqualTo("\"2026-08-12 09:30:00\"");
                });
    }

    private static final class ConsumerValue {
    }

    @RestController
    static class DisabledResponseController {
        @GetMapping("/disabled-response")
        R<String> response() {
            return R.success("payload");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(
            basePackageClasses = ResponseBodyEnhancer.class,
            excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*ResponseBodyEnhancer.*Test.*"))
    private static class WideComponentScanConfiguration {
    }

    private static final class ConsumerValueSerializer extends com.fasterxml.jackson.databind.JsonSerializer<ConsumerValue> {

        @Override
        public void serialize(ConsumerValue value, JsonGenerator generator, SerializerProvider serializers) throws IOException {
            generator.writeString("consumer-module");
        }
    }
}
