package com.yggdrasil.labs.web.config;

import com.yggdrasil.labs.web.interceptor.TraceInterceptor;
import com.yggdrasil.labs.web.interceptor.WebInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Optional;

/**
 * Web MVC 配置
 *
 * <p>功能说明：</p>
 * <ul>
 * <li>注册 Web 拦截器和 Trace 拦截器</li>
 * <li>配置拦截器路径规则</li>
 * </ul>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class WebMvcConfig implements WebMvcConfigurer {

    private final WebInterceptor webInterceptor;

    /**
     * Trace 拦截器（可选）
     * 如果存在则注入，不存在则忽略（由 starter-trace 或 Micrometer Tracing 接管）
     */
    @Autowired(required = false)
    private TraceInterceptor traceInterceptor;

    /**
     * 注册拦截器
     *
     * @param registry 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册 Trace 拦截器（如果存在）
        // 优先级：TraceInterceptor 应该在 WebInterceptor 之前执行
        Optional.ofNullable(traceInterceptor).ifPresent(interceptor ->
                registry.addInterceptor(interceptor)
                        .addPathPatterns("/**")
                        .excludePathPatterns(
                                // 排除静态资源
                                "/favicon.ico",
                                "/error",
                                // 排除健康检查端点
                                "/actuator/**",
                                // 排除 Swagger 相关路径（如果使用）
                                "/swagger-ui/**",
                                "/swagger-resources/**",
                                "/v3/api-docs/**",
                                "/doc.html"
                        )
        );

        // 注册 Web 拦截器
        registry.addInterceptor(webInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        // 排除静态资源
                        "/favicon.ico",
                        "/error",
                        // 排除健康检查端点
                        "/actuator/**",
                        // 排除 Swagger 相关路径（如果使用）
                        "/swagger-ui/**",
                        "/swagger-resources/**",
                        "/v3/api-docs/**",
                        "/doc.html"
                );
    }
}

