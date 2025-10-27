package com.yggdrasil.labs.log.web;

import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.servlet.ConditionalOnMissingFilterBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;

/**
 * 访问日志自动配置
 *
 * <p>功能说明：</p>
 * <ul>
 * <li>自动注册访问日志过滤器</li>
 * <li>支持通过配置文件自定义慢接口阈值</li>
 * <li>可控制是否启用访问日志功能</li>
 * </ul>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
        prefix = "mimir.boot.log.access",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
@EnableConfigurationProperties(AccessLogProperties.class)
public class AccessLogAutoConfiguration {

    private final AccessLogProperties properties;

    public AccessLogAutoConfiguration(AccessLogProperties properties) {
        this.properties = properties;
    }

    /**
     * 注册访问日志过滤器
     */
    @Bean
    @ConditionalOnMissingFilterBean
    @Order(Integer.MIN_VALUE + 1) // 在 Spring Security 之后执行
    public FilterRegistrationBean<Filter> accessLogFilter() {
        FilterRegistrationBean<Filter> registrationBean = new FilterRegistrationBean<>(
                new AccessLogFilter(properties.getSlowThresholdMs())
        );

        registrationBean.setName("accessLogFilter");
        registrationBean.addUrlPatterns("/*");

        return registrationBean;
    }
}

