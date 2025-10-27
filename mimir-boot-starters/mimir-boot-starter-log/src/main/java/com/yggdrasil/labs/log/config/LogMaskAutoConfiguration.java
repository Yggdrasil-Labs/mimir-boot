package com.yggdrasil.labs.log.config;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.pattern.ClassicConverter;
import com.yggdrasil.labs.log.converter.SensitiveDataConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

/**
 * 日志脱敏自动配置
 *
 * <p>功能说明：</p>
 * <ul>
 * <li>自动注册敏感信息脱敏转换器</li>
 * <li>支持通过配置文件自定义脱敏规则</li>
 * <li>提供日志脱敏的开关控制</li>
 * </ul>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass({LoggerContext.class, ClassicConverter.class})
@EnableConfigurationProperties(LogMaskProperties.class)
public class LogMaskAutoConfiguration {

    private final LogMaskProperties properties;

    public LogMaskAutoConfiguration(LogMaskProperties properties) {
        this.properties = properties;
    }

    /**
     * 将 Spring 配置传递给 Logback
     * <p>
     * 注意：Logback 初始化在 Spring 之前，所以需要延迟传递配置
     * 使用 @EventListener(ContextRefreshedEvent.class) 确保在 Spring 完全初始化后再传递配置
     */
    @EventListener(ContextRefreshedEvent.class)
    public void transferConfig(ContextRefreshedEvent event) {
        // 将配置传递给 Logback context
        LoggerContext loggerContext = (LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();

        if (properties.getEnabledPatterns() != null && !properties.getEnabledPatterns().isEmpty()) {
            loggerContext.putProperty(
                    SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY,
                    String.join(",", properties.getEnabledPatterns())
            );
        }

        if (properties.getCustomPatterns() != null && !properties.getCustomPatterns().isEmpty()) {
            loggerContext.putProperty(
                    SensitiveDataConverter.MASK_CUSTOM_PATTERNS_PROPERTY,
                    String.join(",", properties.getCustomPatterns())
            );
        }

        if (properties.getReplacement() != null) {
            loggerContext.putProperty(
                    SensitiveDataConverter.MASK_REPLACEMENT_PROPERTY,
                    properties.getReplacement()
            );
        }
    }
}

