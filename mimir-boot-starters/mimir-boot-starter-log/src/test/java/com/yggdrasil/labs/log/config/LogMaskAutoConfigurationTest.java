package com.yggdrasil.labs.log.config;

import ch.qos.logback.classic.LoggerContext;
import com.yggdrasil.labs.log.converter.SensitiveDataConverter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.support.GenericApplicationContext;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 日志脱敏自动配置测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class LogMaskAutoConfigurationTest {

    private LogMaskAutoConfiguration configuration;
    private LoggerContext loggerContext;
    private GenericApplicationContext applicationContext;

    @BeforeEach
    void setUp() {
        loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        // 清理之前的配置
        loggerContext.putProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY, null);
        loggerContext.putProperty(SensitiveDataConverter.MASK_CUSTOM_PATTERNS_PROPERTY, null);
        loggerContext.putProperty(SensitiveDataConverter.MASK_REPLACEMENT_PROPERTY, null);

        applicationContext = new GenericApplicationContext();
        applicationContext.refresh();
    }

    @AfterEach
    void tearDown() {
        if (applicationContext != null) {
            applicationContext.close();
        }
        // 清理配置
        loggerContext.putProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY, null);
        loggerContext.putProperty(SensitiveDataConverter.MASK_CUSTOM_PATTERNS_PROPERTY, null);
        loggerContext.putProperty(SensitiveDataConverter.MASK_REPLACEMENT_PROPERTY, null);
    }

    /**
     * 测试默认配置（空配置）
     */
    @Test
    void testDefaultConfiguration() {
        LogMaskProperties properties = new LogMaskProperties();
        configuration = new LogMaskAutoConfiguration(properties);

        ContextRefreshedEvent event = new ContextRefreshedEvent(applicationContext);
        configuration.transferConfig(event);

        // 默认配置下，Logback context 中不应该有这些属性
        assertNull(loggerContext.getProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY));
        assertNull(loggerContext.getProperty(SensitiveDataConverter.MASK_CUSTOM_PATTERNS_PROPERTY));
    }

    /**
     * 测试启用预置脱敏规则
     */
    @Test
    void testEnabledPatterns() {
        LogMaskProperties properties = new LogMaskProperties();
        properties.setEnabledPatterns(Arrays.asList("password", "token", "secret"));
        configuration = new LogMaskAutoConfiguration(properties);

        ContextRefreshedEvent event = new ContextRefreshedEvent(applicationContext);
        configuration.transferConfig(event);

        String enabledPatterns = loggerContext.getProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY);
        assertNotNull(enabledPatterns);
        assertEquals("password,token,secret", enabledPatterns);
    }

    /**
     * 测试自定义脱敏规则
     */
    @Test
    void testCustomPatterns() {
        LogMaskProperties properties = new LogMaskProperties();
        properties.setCustomPatterns(Arrays.asList("\\d{4}-\\d{4}-\\d{4}-\\d{4}", "\\d{11}"));
        configuration = new LogMaskAutoConfiguration(properties);

        ContextRefreshedEvent event = new ContextRefreshedEvent(applicationContext);
        configuration.transferConfig(event);

        String customPatterns = loggerContext.getProperty(SensitiveDataConverter.MASK_CUSTOM_PATTERNS_PROPERTY);
        assertNotNull(customPatterns);
        assertEquals("\\d{4}-\\d{4}-\\d{4}-\\d{4},\\d{11}", customPatterns);
    }

    /**
     * 测试替换字符
     */
    @Test
    void testReplacement() {
        LogMaskProperties properties = new LogMaskProperties();
        properties.setReplacement("***MASKED***");
        configuration = new LogMaskAutoConfiguration(properties);

        ContextRefreshedEvent event = new ContextRefreshedEvent(applicationContext);
        configuration.transferConfig(event);

        String replacement = loggerContext.getProperty(SensitiveDataConverter.MASK_REPLACEMENT_PROPERTY);
        assertNotNull(replacement);
        assertEquals("***MASKED***", replacement);
    }

    /**
     * 测试所有配置组合
     */
    @Test
    void testAllPropertiesTogether() {
        LogMaskProperties properties = new LogMaskProperties();
        properties.setEnabledPatterns(Arrays.asList("password", "api_key"));
        properties.setCustomPatterns(Arrays.asList("\\d{16}"));
        properties.setReplacement("****");
        configuration = new LogMaskAutoConfiguration(properties);

        ContextRefreshedEvent event = new ContextRefreshedEvent(applicationContext);
        configuration.transferConfig(event);

        assertEquals("password,api_key",
                loggerContext.getProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY));
        assertEquals("\\d{16}",
                loggerContext.getProperty(SensitiveDataConverter.MASK_CUSTOM_PATTERNS_PROPERTY));
        assertEquals("****",
                loggerContext.getProperty(SensitiveDataConverter.MASK_REPLACEMENT_PROPERTY));
    }

    /**
     * 测试空列表配置
     */
    @Test
    void testEmptyLists() {
        LogMaskProperties properties = new LogMaskProperties();
        properties.setEnabledPatterns(List.of());
        properties.setCustomPatterns(List.of());
        configuration = new LogMaskAutoConfiguration(properties);

        ContextRefreshedEvent event = new ContextRefreshedEvent(applicationContext);
        configuration.transferConfig(event);

        // 空列表不应该设置属性
        assertNull(loggerContext.getProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY));
        assertNull(loggerContext.getProperty(SensitiveDataConverter.MASK_CUSTOM_PATTERNS_PROPERTY));
    }

    /**
     * 测试 null 值配置
     */
    @Test
    void testNullValues() {
        LogMaskProperties properties = new LogMaskProperties();
        properties.setEnabledPatterns(null);
        properties.setCustomPatterns(null);
        properties.setReplacement(null);
        configuration = new LogMaskAutoConfiguration(properties);

        ContextRefreshedEvent event = new ContextRefreshedEvent(applicationContext);
        configuration.transferConfig(event);

        // null 值不应该设置属性
        assertNull(loggerContext.getProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY));
        assertNull(loggerContext.getProperty(SensitiveDataConverter.MASK_CUSTOM_PATTERNS_PROPERTY));
        assertNull(loggerContext.getProperty(SensitiveDataConverter.MASK_REPLACEMENT_PROPERTY));
    }

    /**
     * 测试多次调用 transferConfig
     */
    @Test
    void testMultipleTransferConfig() {
        LogMaskProperties properties = new LogMaskProperties();
        properties.setEnabledPatterns(Arrays.asList("password"));
        configuration = new LogMaskAutoConfiguration(properties);

        ContextRefreshedEvent event = new ContextRefreshedEvent(applicationContext);

        // 第一次调用
        configuration.transferConfig(event);
        assertEquals("password",
                loggerContext.getProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY));

        // 更新配置并再次调用
        properties.setEnabledPatterns(Arrays.asList("token", "secret"));
        configuration.transferConfig(event);
        assertEquals("token,secret",
                loggerContext.getProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY));
    }

    /**
     * 测试单个模式
     */
    @Test
    void testSinglePattern() {
        LogMaskProperties properties = new LogMaskProperties();
        properties.setEnabledPatterns(Arrays.asList("password"));
        configuration = new LogMaskAutoConfiguration(properties);

        ContextRefreshedEvent event = new ContextRefreshedEvent(applicationContext);
        configuration.transferConfig(event);

        assertEquals("password",
                loggerContext.getProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY));
    }

    /**
     * 测试配置对象的创建
     */
    @Test
    void testConfigurationCreation() {
        LogMaskProperties properties = new LogMaskProperties();
        configuration = new LogMaskAutoConfiguration(properties);

        assertNotNull(configuration);
    }
}

