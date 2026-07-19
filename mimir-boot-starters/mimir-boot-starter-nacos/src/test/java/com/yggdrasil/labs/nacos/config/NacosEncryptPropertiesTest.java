package com.yggdrasil.labs.nacos.config;

import com.yggdrasil.labs.test.base.BaseUnitTest;
import com.yggdrasil.labs.test.util.AssertUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Nacos 配置加密属性测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class NacosEncryptPropertiesTest extends BaseUnitTest {

    @Test
    void testDefaultValues() {
        NacosEncryptProperties properties = new NacosEncryptProperties();

        assertTrue(properties.getEnabled());
        assertNull(properties.getKey());
        AssertUtils.assertEquals("AES/GCM/NoPadding", properties.getAlgorithm());
        AssertUtils.assertEquals("ENC", properties.getPrefix());
    }

    @Test
    void testSetterAndGetter() {
        NacosEncryptProperties properties = new NacosEncryptProperties();

        properties.setEnabled(false);
        properties.setKey("test-key");
        properties.setAlgorithm("DES");
        properties.setPrefix("ENCRYPTED");

        assertFalse(properties.getEnabled());
        AssertUtils.assertEquals("test-key", properties.getKey());
        AssertUtils.assertEquals("DES", properties.getAlgorithm());
        AssertUtils.assertEquals("ENCRYPTED", properties.getPrefix());
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

        AssertUtils.assertEquals(customKey, properties.getKey());
    }

    @Test
    void testCustomAlgorithm() {
        NacosEncryptProperties properties = new NacosEncryptProperties();
        String customAlgorithm = "AES256";

        properties.setAlgorithm(customAlgorithm);

        AssertUtils.assertEquals(customAlgorithm, properties.getAlgorithm());
    }

    @Test
    void testCustomPrefix() {
        NacosEncryptProperties properties = new NacosEncryptProperties();
        String customPrefix = "SECRET";

        properties.setPrefix(customPrefix);

        AssertUtils.assertEquals(customPrefix, properties.getPrefix());
    }
}
