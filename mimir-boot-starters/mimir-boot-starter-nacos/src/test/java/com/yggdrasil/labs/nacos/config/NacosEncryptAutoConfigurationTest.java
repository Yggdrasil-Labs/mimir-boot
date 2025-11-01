package com.yggdrasil.labs.nacos.config;

import com.yggdrasil.labs.nacos.crypto.ConfigCryptoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Nacos 配置加密自动配置测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class NacosEncryptAutoConfigurationTest {

    private NacosEncryptProperties properties;
    private StandardEnvironment environment;
    private NacosEncryptAutoConfiguration configuration;
    private String testKey;

    @BeforeEach
    void setUp() {
        properties = new NacosEncryptProperties();
        testKey = ConfigCryptoUtils.generateKey();
        properties.setKey(testKey);
        properties.setEnabled(true);

        configuration = new NacosEncryptAutoConfiguration(properties);
        environment = new StandardEnvironment();
    }

    @Test
    void testConfigurationCreation() {
        assertNotNull(configuration);
    }

    @Test
    void testProcessDecryptWithEncryptedValue() {
        // 准备加密的配置值
        String plaintext = "secret-value";
        String encrypted = ConfigCryptoUtils.encrypt(plaintext, testKey);
        String encValue = "ENC(" + encrypted + ")";

        Map<String, Object> props = new HashMap<>();
        props.put("app.secret", encValue);

        MapPropertySource propertySource = new MapPropertySource("test", props);
        environment.getPropertySources().addFirst(propertySource);

        // 直接调用处理解密方法
        configuration.processDecrypt(environment);

        // 验证解密结果
        assertEquals(plaintext, environment.getProperty("app.secret"));
    }

    @Test
    void testProcessDecryptWithDisabled() {
        properties.setEnabled(false);

        Map<String, Object> props = new HashMap<>();
        String encrypted = ConfigCryptoUtils.encrypt("secret", testKey);
        props.put("app.secret", "ENC(" + encrypted + ")");

        MapPropertySource propertySource = new MapPropertySource("test", props);
        environment.getPropertySources().addFirst(propertySource);

        configuration.processDecrypt(environment);

        // 禁用时不应该解密
        String value = environment.getProperty("app.secret");
        assertTrue(value.startsWith("ENC("));
    }

    @Test
    void testProcessDecryptWithNoKey() {
        properties.setKey(null);

        Map<String, Object> props = new HashMap<>();
        props.put("app.secret", "ENC(value)");

        MapPropertySource propertySource = new MapPropertySource("test", props);
        environment.getPropertySources().addFirst(propertySource);

        // 无密钥时不应该抛出异常
        assertDoesNotThrow(() -> configuration.processDecrypt(environment));
    }

    @Test
    void testProcessDecryptWithEmptyKey() {
        properties.setKey("");

        Map<String, Object> props = new HashMap<>();
        props.put("app.secret", "ENC(value)");

        MapPropertySource propertySource = new MapPropertySource("test", props);
        environment.getPropertySources().addFirst(propertySource);

        // 空密钥时不应该抛出异常
        assertDoesNotThrow(() -> configuration.processDecrypt(environment));
    }

    @Test
    void testProcessDecryptWithMultipleEncryptedValues() {
        String secret1 = ConfigCryptoUtils.encrypt("secret1", testKey);
        String secret2 = ConfigCryptoUtils.encrypt("secret2", testKey);

        Map<String, Object> props = new HashMap<>();
        props.put("app.secret1", "ENC(" + secret1 + ")");
        props.put("app.secret2", "ENC(" + secret2 + ")");
        props.put("app.name", "MyApp");

        MapPropertySource propertySource = new MapPropertySource("test", props);
        environment.getPropertySources().addFirst(propertySource);

        configuration.processDecrypt(environment);

        assertEquals("secret1", environment.getProperty("app.secret1"));
        assertEquals("secret2", environment.getProperty("app.secret2"));
        assertEquals("MyApp", environment.getProperty("app.name"));
    }

    @Test
    void testProcessDecryptWithNonEncryptedValues() {
        Map<String, Object> props = new HashMap<>();
        props.put("app.name", "MyApp");
        props.put("app.version", "1.0.0");

        MapPropertySource propertySource = new MapPropertySource("test", props);
        environment.getPropertySources().addFirst(propertySource);

        configuration.processDecrypt(environment);

        // 未加密的值应该保持不变
        assertEquals("MyApp", environment.getProperty("app.name"));
        assertEquals("1.0.0", environment.getProperty("app.version"));
    }

    @Test
    void testProcessDecryptAvoidDuplicateProcessing() {
        String plaintext = "secret";
        String encrypted = ConfigCryptoUtils.encrypt(plaintext, testKey);

        Map<String, Object> props = new HashMap<>();
        props.put("app.secret", "ENC(" + encrypted + ")");

        MapPropertySource propertySource = new MapPropertySource("test", props);
        environment.getPropertySources().addFirst(propertySource);

        // 第一次处理
        configuration.processDecrypt(environment);
        assertEquals(plaintext, environment.getProperty("app.secret"));

        // 第二次处理应该被跳过
        configuration.processDecrypt(environment);
        assertEquals(plaintext, environment.getProperty("app.secret"));
    }
}
