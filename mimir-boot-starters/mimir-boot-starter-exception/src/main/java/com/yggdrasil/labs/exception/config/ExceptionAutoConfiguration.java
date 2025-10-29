package com.yggdrasil.labs.exception.config;

import com.yggdrasil.labs.exception.handler.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 全局异常处理自动配置
 *
 * <p>功能说明：</p>
 * <ul>
 * <li>自动注册全局异常处理器</li>
 * <li>支持通过配置文件控制异常处理行为</li>
 * <li>统一处理业务异常、系统异常、参数校验异常等</li>
 * </ul>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnWebApplication
@ConditionalOnProperty(
        prefix = "mimir.boot.exception",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
@EnableConfigurationProperties(ExceptionProperties.class)
public class ExceptionAutoConfiguration {

    /**
     * 注册全局异常处理器
     *
     * @return 全局异常处理器
     */
    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}

