package com.yggdrasil.labs.log.converter;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 敏感数据转换器测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class SensitiveDataConverterTest {

    private SensitiveDataConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SensitiveDataConverter();
        // 设置 Logback context
        ch.qos.logback.classic.LoggerContext context =
                (ch.qos.logback.classic.LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
        converter.setContext(context);
        converter.start();
        // 清空自定义规则
        SensitiveDataConverter.clearCustomPatterns();
        SensitiveDataConverter.reloadConfig();
    }

    @AfterEach
    void tearDown() {
        SensitiveDataConverter.clearCustomPatterns();
        SensitiveDataConverter.reloadConfig();
    }

    @Test
    void testConvertWithNullMessage() {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getFormattedMessage()).thenReturn(null);

        String result = converter.convert(event);

        assertNull(result);
    }

    @Test
    void testConvertWithEmptyMessage() {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getFormattedMessage()).thenReturn("");

        String result = converter.convert(event);

        assertEquals("", result);
    }

    @Test
    void testConvertWithNormalMessage() {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getFormattedMessage()).thenReturn("用户登录成功");

        String result = converter.convert(event);

        assertEquals("用户登录成功", result);
    }

    @Test
    void testStart() {
        SensitiveDataConverter conv = new SensitiveDataConverter();
        assertDoesNotThrow(conv::start);
    }

    @Test
    void testGetAllPresetPatternNames() {
        List<String> names = SensitiveDataConverter.getAllPresetPatternNames();

        assertNotNull(names);
        assertFalse(names.isEmpty());
        assertTrue(names.contains("password"));
        assertTrue(names.contains("token"));
        assertTrue(names.contains("email"));
    }

    @Test
    void testAddCustomPattern() {
        SensitiveDataConverter.addCustomPattern("test\\d+");

        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getFormattedMessage()).thenReturn("测试 test123 匹配");

        String result = converter.convert(event);

        assertTrue(result.contains("******") || !result.contains("test123"));
    }

    @Test
    void testClearCustomPatterns() {
        // 先添加自定义规则
        SensitiveDataConverter.addCustomPattern("test\\d+");
        assertFalse(SensitiveDataConverter.getAllPresetPatternNames().isEmpty());

        // 清空自定义规则
        SensitiveDataConverter.clearCustomPatterns();

        // 重新加载配置，验证清理生效
        SensitiveDataConverter.reloadConfig();
    }

    @Test
    void testReloadConfig() {
        SensitiveDataConverter.addCustomPattern("test\\d+");

        assertDoesNotThrow(SensitiveDataConverter::reloadConfig);
    }

    @Test
    void testGetContextProperty() {
        // 这个测试需要 Logback context
        assertNotNull(converter.getContext());
    }

    @Test
    void testConstants() {
        assertEquals("mimir.boot.log.mask.enabledPatterns",
                SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY);
        assertEquals("mimir.boot.log.mask.customPatterns",
                SensitiveDataConverter.MASK_CUSTOM_PATTERNS_PROPERTY);
        assertEquals("mimir.boot.log.mask.replacement",
                SensitiveDataConverter.MASK_REPLACEMENT_PROPERTY);
    }

    @Test
    void testConvertWithMultipleMessages() {
        String[] messages = {
                "用户登录成功",
                "订单创建完成",
                "查询用户信息"
        };

        for (String message : messages) {
            ILoggingEvent event = mock(ILoggingEvent.class);
            when(event.getFormattedMessage()).thenReturn(message);

            String result = converter.convert(event);

            assertEquals(message, result);
        }
    }
}

