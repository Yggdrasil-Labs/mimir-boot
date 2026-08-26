package com.yggdrasil.labs.log.config;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.pattern.ClassicConverter;
import com.yggdrasil.labs.log.converter.SensitiveDataConverter;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(LogMaskAutoConfiguration.class);

    private final LogMaskProperties properties;
    private boolean nonLogbackWarningRecorded;

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
        ILoggerFactory loggerFactory = LoggerFactory.getILoggerFactory();
        if (!(loggerFactory instanceof LoggerContext loggerContext)) {
            warnNonLogbackOnce(event);
            return;
        }

        putListProperty(loggerContext, SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY,
                properties.getEnabledPatterns());
        putListProperty(loggerContext, SensitiveDataConverter.MASK_CUSTOM_PATTERNS_PROPERTY,
                properties.getCustomPatterns());
        loggerContext.putProperty(SensitiveDataConverter.MASK_REPLACEMENT_PROPERTY, properties.getReplacement());
        SensitiveDataConverter.publishConfiguration(
                properties.getEnabledPatterns(), properties.getCustomPatterns(), properties.getReplacement());
    }

    private static void putListProperty(LoggerContext loggerContext, String key, java.util.List<String> values) {
        loggerContext.putProperty(key, values == null || values.isEmpty() ? null : String.join(",", values));
    }

    private synchronized void warnNonLogbackOnce(ContextRefreshedEvent event) {
        if (!nonLogbackWarningRecorded) {
            nonLogbackWarningRecorded = true;
            LOGGER.warn("ApplicationContext [{}] 未使用 Logback，跳过日志脱敏转换器注册",
                    event.getApplicationContext().getId());
        }
    }
}
