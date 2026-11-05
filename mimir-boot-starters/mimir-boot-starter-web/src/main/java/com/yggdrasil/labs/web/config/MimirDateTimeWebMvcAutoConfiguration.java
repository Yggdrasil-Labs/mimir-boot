package com.yggdrasil.labs.web.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** 仅在 Spring Boot 接管 Servlet MVC 时补充 Java Time 参数的缺省格式。 */
@AutoConfiguration(after = WebMvcAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
        prefix = "mimir.boot.web",
        name = {"enabled", "mvc.date-format-enabled"},
        havingValue = "true",
        matchIfMissing = true)
@ConditionalOnBean(WebMvcAutoConfiguration.EnableWebMvcConfiguration.class)
@EnableConfigurationProperties(WebProperties.class)
public class MimirDateTimeWebMvcAutoConfiguration {

    /**
     * 注册配置器；格式器仅在 MVC 转换服务内创建，不暴露为可被 Boot 重复注册的 Bean。
     *
     * @param properties Web 增强配置
     * @param mvcProperties Spring MVC 配置
     * @return 日期参数配置器
     */
    @Bean
    MimirDateTimeWebMvcConfigurer mimirDateTimeWebMvcConfigurer(
            WebProperties properties, WebMvcProperties mvcProperties) {
        return new MimirDateTimeWebMvcConfigurer(properties, mvcProperties);
    }
}
