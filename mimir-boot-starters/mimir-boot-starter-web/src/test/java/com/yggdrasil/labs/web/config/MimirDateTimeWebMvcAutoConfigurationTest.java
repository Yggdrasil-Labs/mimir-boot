package com.yggdrasil.labs.web.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.format.Formatter;
import org.springframework.format.Parser;
import org.springframework.format.Printer;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/** 验证日期自动配置在 Boot MVC 之后评估条件且不泄漏格式器 Bean。 */
class MimirDateTimeWebMvcAutoConfigurationTest {

    private final WebApplicationContextRunner runner =
            new WebApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    MimirDateTimeWebMvcAutoConfiguration.class,
                                    WebMvcAutoConfiguration.class));

    @Test
    void configuresAfterBootMvcWithoutExposingFormatterBeans() {
        runner.run(
                context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context)
                            .hasSingleBean(WebMvcAutoConfiguration.EnableWebMvcConfiguration.class)
                            .hasSingleBean(MimirDateTimeWebMvcConfigurer.class);
                    assertThat(context.getBean(MimirDateTimeWebMvcConfigurer.class).getOrder())
                            .isEqualTo(-1);
                    assertThat(context.getBeansOfType(Formatter.class)).isEmpty();
                    assertThat(context.getBeansOfType(Printer.class)).isEmpty();
                    assertThat(context.getBeansOfType(Parser.class)).isEmpty();
                    assertThat(context.getBeansOfType(Converter.class)).isEmpty();
                    assertThat(context.getBeansOfType(GenericConverter.class)).isEmpty();
                });
    }

    @Test
    void backsOffForEitherSwitch() {
        for (String property :
                new String[] {
                    "mimir.boot.web.enabled=false", "mimir.boot.web.mvc.date-format-enabled=false"
                }) {
            runner.withPropertyValues(property)
                    .run(
                            context ->
                                    assertThat(context)
                                            .hasNotFailed()
                                            .doesNotHaveBean(MimirDateTimeWebMvcConfigurer.class));
        }
    }

    @Test
    void backsOffWhenApplicationTakesOverMvc() {
        runner.withUserConfiguration(OwnedMvcConfiguration.class)
                .run(
                        context ->
                                assertThat(context)
                                        .hasNotFailed()
                                        .doesNotHaveBean(
                                                WebMvcAutoConfiguration.EnableWebMvcConfiguration
                                                        .class)
                                        .doesNotHaveBean(MimirDateTimeWebMvcConfigurer.class));
    }

    @Test
    void requiresBootMvcBean() {
        new WebApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(MimirDateTimeWebMvcAutoConfiguration.class))
                .run(
                        context ->
                                assertThat(context)
                                        .hasNotFailed()
                                        .doesNotHaveBean(MimirDateTimeWebMvcConfigurer.class));
    }

    @Test
    void backsOffOutsideServletApplication() {
        AutoConfigurations configurations =
                AutoConfigurations.of(
                        MimirDateTimeWebMvcAutoConfiguration.class, WebMvcAutoConfiguration.class);
        new ApplicationContextRunner()
                .withConfiguration(configurations)
                .run(
                        context ->
                                assertThat(context)
                                        .hasNotFailed()
                                        .doesNotHaveBean(MimirDateTimeWebMvcConfigurer.class));
        new ReactiveWebApplicationContextRunner()
                .withConfiguration(configurations)
                .run(
                        context ->
                                assertThat(context)
                                        .hasNotFailed()
                                        .doesNotHaveBean(MimirDateTimeWebMvcConfigurer.class));
    }

    @Test
    void invalidMimirPatternsFailStartupWithPropertyName() {
        for (String property :
                new String[] {
                    "mimir.boot.web.serialization.date-format",
                    "mimir.boot.web.serialization.time-format",
                    "mimir.boot.web.serialization.date-time-format"
                }) {
            runner.withPropertyValues(property + "=invalid[")
                    .run(
                            context ->
                                    assertThat(context)
                                            .hasFailed()
                                            .getFailure()
                                            .hasStackTraceContaining("无效的日期格式配置: " + property));
        }
    }

    @Test
    void invalidBootPatternDoesNotFallBackToMimir() {
        runner.withPropertyValues("spring.mvc.format.date-time=invalid[")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class OwnedMvcConfiguration {}
}
