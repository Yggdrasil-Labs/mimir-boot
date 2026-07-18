package com.yggdrasil.labs.web.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * CORS 跨域配置
 *
 * <p>功能说明：</p>
 * <ul>
 * <li>统一配置跨域资源共享策略</li>
 * <li>支持通过配置文件自定义跨域规则</li>
 * <li>提供合理的默认配置</li>
 * </ul>
 *
 * <p>Source: https://docs.spring.io/spring-framework/reference/web/webmvc-cors.html</p>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "mimir.boot.web.cors",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class CorsConfig {

    private final WebProperties webProperties;

    /**
     * 配置 CORS 过滤器
     *
     * @return CORS 过滤器
     */
    @Bean
    public CorsFilter corsFilter() {
        WebProperties.Cors corsConfig = webProperties.getCors();
        CorsConfiguration configuration = new CorsConfiguration();

        List<String> allowedOrigins = corsConfig.getAllowedOrigins();
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            throw new IllegalStateException("启用 CORS 时必须配置至少一个允许的 Origin");
        }
        configuration.setAllowedOrigins(allowedOrigins);

        // 设置允许的 HTTP 方法
        if (corsConfig.getAllowedMethods() != null && !corsConfig.getAllowedMethods().isEmpty()) {
            corsConfig.getAllowedMethods().forEach(method -> 
                    configuration.addAllowedMethod(method.toUpperCase()));
        }

        // 设置允许的请求头
        if (corsConfig.getAllowedHeaders() != null && !corsConfig.getAllowedHeaders().isEmpty()) {
            if (corsConfig.getAllowedHeaders().contains("*")) {
                configuration.addAllowedHeader("*");
            } else {
                corsConfig.getAllowedHeaders().forEach(configuration::addAllowedHeader);
            }
        }

        // 设置是否允许携带凭证
        configuration.setAllowCredentials(corsConfig.isAllowCredentials());
        configuration.validateAllowCredentials();

        // 设置预检请求的有效期
        if (corsConfig.getMaxAge() != null) {
            configuration.setMaxAge(corsConfig.getMaxAge().getSeconds());
        }

        // 设置暴露的响应头
        if (corsConfig.getExposedHeaders() != null && !corsConfig.getExposedHeaders().isEmpty()) {
            corsConfig.getExposedHeaders().forEach(configuration::addExposedHeader);
        }

        // 创建 URL 基于的 CORS 配置源
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return new CorsFilter(source);
    }
}
