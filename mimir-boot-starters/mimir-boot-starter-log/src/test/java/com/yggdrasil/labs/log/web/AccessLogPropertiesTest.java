package com.yggdrasil.labs.log.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 访问日志配置属性测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class AccessLogPropertiesTest {

    @Test
    void testDefaultValues() {
        AccessLogProperties properties = new AccessLogProperties();

        assertTrue(properties.isEnabled(), "默认应启用访问日志");
        assertEquals(1000, properties.getSlowThresholdMs(), "默认慢接口阈值应为 1000ms");
    }

    @Test
    void testEnabledProperty() {
        AccessLogProperties properties = new AccessLogProperties();

        properties.setEnabled(false);
        assertFalse(properties.isEnabled());

        properties.setEnabled(true);
        assertTrue(properties.isEnabled());
    }

    @Test
    void testSlowThresholdMsProperty() {
        AccessLogProperties properties = new AccessLogProperties();

        properties.setSlowThresholdMs(500);
        assertEquals(500, properties.getSlowThresholdMs());

        properties.setSlowThresholdMs(2000);
        assertEquals(2000, properties.getSlowThresholdMs());

        properties.setSlowThresholdMs(5000);
        assertEquals(5000, properties.getSlowThresholdMs());
    }
}

