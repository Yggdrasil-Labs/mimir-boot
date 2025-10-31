package com.yggdrasil.labs.web.config;

import com.yggdrasil.labs.web.advice.ResponseBodyEnhancer;
import com.yggdrasil.labs.web.interceptor.TraceInterceptor;
import com.yggdrasil.labs.web.interceptor.WebInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Web 层自动配置
 *
 * <p>功能说明：</p>
 * <ul>
 * <li>自动配置 Web 层通用特性</li>
 * <li>注册响应体增强器、拦截器等组件</li>
 * <li>支持通过配置文件控制功能开关</li>
 * </ul>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
        prefix = "mimir.boot.web",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
@EnableConfigurationProperties(WebProperties.class)
@Import({JacksonConfig.class, CorsConfig.class, WebMvcConfig.class})
public class WebAutoConfiguration {

    /**
     * 注册响应体增强器
     *
     * @param webProperties Web 配置属性
     * @return 响应体增强器
     */
    @Bean
    public ResponseBodyEnhancer responseBodyEnhancer(WebProperties webProperties) {
        return new ResponseBodyEnhancer(webProperties);
    }

    /**
     * 注册 Trace 拦截器
     * <p>
     * 当检测到 classpath 中存在 Micrometer Tracer 时，此 Bean 不会被创建
     * 由 starter-trace 模块接管 Trace 逻辑（提供 traceInterceptor Bean）
     * </p>
     * <p>
     * 条件说明：
     * - @ConditionalOnMissingBean: 如果 starter-trace 已提供 traceInterceptor，则不创建
     * - @ConditionalOnMissingClass: 如果 classpath 中存在 Micrometer Tracer，则不创建
     * </p>
     *
     * @return Trace 拦截器
     */
    @Bean(name = "traceInterceptor")
    @ConditionalOnMissingBean(name = "traceInterceptor")
    @ConditionalOnMissingClass("io.micrometer.tracing.Tracer")
    public TraceInterceptor traceInterceptor() {
        // 仅在以下情况创建：
        // 1. 不存在 traceInterceptor Bean（starter-trace 未提供）
        // 2. classpath 中不存在 Micrometer Tracer
        return new TraceInterceptor();
    }

    /**
     * 注册 Web 拦截器
     *
     * @return Web 拦截器
     */
    @Bean
    public WebInterceptor webInterceptor() {
        return new WebInterceptor();
    }
}

