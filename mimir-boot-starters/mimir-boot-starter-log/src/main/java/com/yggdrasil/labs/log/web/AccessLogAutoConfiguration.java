package com.yggdrasil.labs.log.web;

import jakarta.servlet.Filter;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * 访问日志自动配置
 *
 * <p>功能说明：
 *
 * <ul>
 *   <li>自动注册访问日志过滤器
 *   <li>支持通过配置文件自定义慢接口阈值
 *   <li>可控制是否启用访问日志功能
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
        matchIfMissing = true)
@EnableConfigurationProperties(AccessLogProperties.class)
public class AccessLogAutoConfiguration {

    private final AccessLogProperties properties;

    public AccessLogAutoConfiguration(AccessLogProperties properties) {
        this.properties = properties;
    }

    /** 注册访问日志过滤器 */
    @Bean
    @ConditionalOnMissingBean(name = "accessLogFilter")
    public FilterRegistrationBean<Filter> accessLogFilter() {
        FilterRegistrationBean<Filter> registrationBean =
                new FilterRegistrationBean<>(
                        new AccessLogFilter(
                                properties.getSlowThresholdMs(), properties.getExcludePaths()));

        registrationBean.setName("accessLogFilter");
        registrationBean.addUrlPatterns("/*");
        registrationBean.setAsyncSupported(true);
        // 设置较高优先级，确保在其他业务 Filter 之前执行；须位于 Boot ServerHttpObservationFilter
        // （HIGHEST_PRECEDENCE + 1）之内，写访问日志时 tracing scope 尚未关闭，traceId/spanId 可用
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);

        return registrationBean;
    }
}
