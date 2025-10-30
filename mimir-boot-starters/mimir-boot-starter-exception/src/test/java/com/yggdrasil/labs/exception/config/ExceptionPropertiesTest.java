package com.yggdrasil.labs.exception.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 异常处理配置属性测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class ExceptionPropertiesTest {

    @Test
    void testDefaultValues() {
        ExceptionProperties properties = new ExceptionProperties();

        assertTrue(properties.isEnabled(), "默认应启用全局异常处理");
    }

    @Test
    void testEnabledProperty() {
        ExceptionProperties properties = new ExceptionProperties();

        properties.setEnabled(false);
        assertFalse(properties.isEnabled());

        properties.setEnabled(true);
        assertTrue(properties.isEnabled());
    }
}

