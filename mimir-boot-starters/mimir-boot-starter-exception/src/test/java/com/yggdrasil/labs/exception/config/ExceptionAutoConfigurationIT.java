package com.yggdrasil.labs.exception.config;

import com.yggdrasil.labs.exception.handler.DefaultExceptionResponseFactory;
import com.yggdrasil.labs.exception.handler.ExceptionResponseFactory;
import com.yggdrasil.labs.exception.handler.MimirExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 异常处理自动配置集成测试
 *
 * <p>验证 ExceptionAutoConfiguration 在 Web 环境下的 Bean 注册和覆盖行为</p>
 *
 * @author Yggdrasil Labs
 * @since 2.1.0
 */
class ExceptionAutoConfigurationIT {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
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
    void customFactoryOverridesDefault() {
        runner.withBean(ExceptionResponseFactory.class, () -> (code, msg, data) -> "custom")
                .run(ctx -> {
                    assertThat(ctx).doesNotHaveBean(DefaultExceptionResponseFactory.class);
                    assertThat(ctx).hasSingleBean(ExceptionResponseFactory.class);
                });
    }

    @Test
    void beansNotRegisteredWhenDisabled() {
        runner.withPropertyValues("mimir.boot.exception.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(MimirExceptionHandler.class));
    }
}
