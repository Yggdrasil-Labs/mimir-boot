package com.yggdrasil.labs.nacos.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Nacos 配置加密属性测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class NacosEncryptPropertiesTest {

    @Test
    void testDefaultValues() {
        NacosEncryptProperties properties = new NacosEncryptProperties();

        assertTrue(properties.getEnabled());
        assertNull(properties.getKey());
        assertEquals("AES", properties.getAlgorithm());
        assertEquals("ENC", properties.getPrefix());
    }

    @Test
    void testSetterAndGetter() {
        NacosEncryptProperties properties = new NacosEncryptProperties();

        properties.setEnabled(false);
        properties.setKey("test-key");
        properties.setAlgorithm("DES");
        properties.setPrefix("ENCRYPTED");

        assertFalse(properties.getEnabled());
        assertEquals("test-key", properties.getKey());
        assertEquals("DES", properties.getAlgorithm());
        assertEquals("ENCRYPTED", properties.getPrefix());
    }

    @Test
    void testEnabledTrue() {
        NacosEncryptProperties properties = new NacosEncryptProperties();
        properties.setEnabled(true);

        assertTrue(properties.getEnabled());
    }

    @Test
    void testEnabledFalse() {
        NacosEncryptProperties properties = new NacosEncryptProperties();
        properties.setEnabled(false);

        assertFalse(properties.getEnabled());
    }

    @Test
    void testCustomKey() {
        NacosEncryptProperties properties = new NacosEncryptProperties();
        String customKey = "custom-base64-key-12345";

        properties.setKey(customKey);

        assertEquals(customKey, properties.getKey());
    }

    @Test
    void testCustomAlgorithm() {
        NacosEncryptProperties properties = new NacosEncryptProperties();
        String customAlgorithm = "AES256";

        properties.setAlgorithm(customAlgorithm);

        assertEquals(customAlgorithm, properties.getAlgorithm());
    }

    @Test
    void testCustomPrefix() {
        NacosEncryptProperties properties = new NacosEncryptProperties();
        String customPrefix = "SECRET";

        properties.setPrefix(customPrefix);

        assertEquals(customPrefix, properties.getPrefix());
    }
}
