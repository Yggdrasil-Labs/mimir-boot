package com.yggdrasil.labs.log.config;

import java.util.List;

import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

import com.yggdrasil.labs.log.converter.MaskListCodec;
import com.yggdrasil.labs.log.converter.SensitiveDataConverter;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.pattern.ClassicConverter;

/**
 * 日志脱敏自动配置
 *
 * <p>功能说明：
 *
 * <ul>
 *   <li>自动注册敏感信息脱敏转换器
 *   <li>支持通过配置文件自定义脱敏规则
 *   <li>提供日志脱敏的开关控制
 * </ul>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass({LoggerContext.class, ClassicConverter.class})
@EnableConfigurationProperties(LogMaskProperties.class)
public class LogMaskAutoConfiguration implements ApplicationContextAware {

    private static final Logger LOGGER = LoggerFactory.getLogger(LogMaskAutoConfiguration.class);

    private final LogMaskProperties properties;
    private boolean nonLogbackWarningRecorded;
    private ApplicationContext applicationContext;

    public LogMaskAutoConfiguration(LogMaskProperties properties) {
        this.properties = properties;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 将 Spring 配置传递给 Logback
     *
     * <p>注意：Logback 初始化在 Spring 之前，所以需要延迟传递配置 使用 @EventListener(ContextRefreshedEvent.class) 确保在
     * Spring 完全初始化后再传递配置
     */
    // 事件必须属于同一个上下文实例，内容相等的其他上下文不能覆盖本实例配置。
    @SuppressWarnings("PMD.CompareObjectsWithEquals")
    @EventListener(ContextRefreshedEvent.class)
    public void transferConfig(ContextRefreshedEvent event) {
        ApplicationContext refreshed = event.getApplicationContext();
        // 只处理本上下文的刷新，避免父上下文经事件传播以自身配置覆盖
        if (applicationContext != null && refreshed != applicationContext) {
            return;
        }
        Environment environment = refreshed.getEnvironment();
        // ① Spring Cloud legacy bootstrap 上下文不发布（D-100）
        if (LogPresetSupport.isBootstrapEnvironment(environment)) {
            return;
        }
        ILoggerFactory loggerFactory = LoggerFactory.getILoggerFactory();
        if (!(loggerFactory instanceof LoggerContext loggerContext)) {
            warnNonLogbackOnce(event);
            return;
        }
        // ② 预设应用的普通子上下文不覆盖父规则：子 Environment 合并父源后优先级可能倒置；③ 其余“最后刷新者发布”
        if (isChildOfPresetApplication(refreshed)) {
            return;
        }
        LogPresetSupport.Preset preset = LogPresetSupport.resolve(environment);
        List<String> enabledPatterns =
                preset != null
                        ? LogPresetSupport.selectEnabledPatterns(environment, preset)
                        : properties.getEnabledPatterns();

        putListProperty(
                loggerContext,
                SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY,
                enabledPatterns);
        putListProperty(
                loggerContext,
                SensitiveDataConverter.MASK_CUSTOM_PATTERNS_PROPERTY,
                properties.getCustomPatterns());
        loggerContext.putProperty(
                SensitiveDataConverter.MASK_REPLACEMENT_PROPERTY, properties.getReplacement());
        SensitiveDataConverter.publishConfiguration(
                enabledPatterns, properties.getCustomPatterns(), properties.getReplacement());
    }

    /** 父 Environment 含内部源即父应用选择了预设；legacy bootstrap 父上下文的内部源已由 EPP 移除 */
    private static boolean isChildOfPresetApplication(ApplicationContext context) {
        ApplicationContext parent = context.getParent();
        return parent != null
                && parent.getEnvironment() instanceof ConfigurableEnvironment environment
                && environment
                        .getPropertySources()
                        .contains(LogPresetSupport.RESOLVED_PROPERTY_SOURCE);
    }

    private static void putListProperty(
            LoggerContext loggerContext, String key, java.util.List<String> values) {
        loggerContext.putProperty(key, MaskListCodec.join(values));
    }

    private synchronized void warnNonLogbackOnce(ContextRefreshedEvent event) {
        if (!nonLogbackWarningRecorded) {
            nonLogbackWarningRecorded = true;
            LOGGER.warn(
                    "ApplicationContext [{}] 未使用 Logback，跳过日志脱敏转换器注册",
                    event.getApplicationContext().getId());
        }
    }
}
