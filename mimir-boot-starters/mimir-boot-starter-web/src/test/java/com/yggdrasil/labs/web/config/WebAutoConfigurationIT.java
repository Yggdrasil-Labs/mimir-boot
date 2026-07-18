package com.yggdrasil.labs.web.config;

import com.yggdrasil.labs.web.advice.ResponseBodyEnhancer;
import com.yggdrasil.labs.web.interceptor.TraceInterceptor;
import com.yggdrasil.labs.web.interceptor.WebInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

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
    void traceInterceptorRegistered() {
        runner.run(ctx -> assertThat(ctx).hasSingleBean(TraceInterceptor.class));
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
    void webPropertiesRegistered() {
        runner.run(ctx -> assertThat(ctx).hasSingleBean(WebProperties.class));
    }

    @Test
    void webInterceptorRegistered() {
        runner.run(ctx -> assertThat(ctx).hasSingleBean(WebInterceptor.class));
    }

    @Test
    void webMvcConfigRegistered() {
        runner.run(ctx -> assertThat(ctx).hasSingleBean(WebMvcConfig.class));
    }

    @Test
    void jacksonConfigRegistered() {
        runner.run(ctx -> assertThat(ctx).hasSingleBean(JacksonConfig.class));
    }
}
