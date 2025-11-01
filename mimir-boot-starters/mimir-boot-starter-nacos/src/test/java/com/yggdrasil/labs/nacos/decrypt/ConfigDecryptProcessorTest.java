package com.yggdrasil.labs.nacos.decrypt;

import com.yggdrasil.labs.nacos.config.NacosEncryptProperties;
import com.yggdrasil.labs.nacos.crypto.ConfigCryptoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 配置解密处理器测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class ConfigDecryptProcessorTest {

    private NacosEncryptProperties properties;
    private StandardEnvironment environment;
    private String testKey;

    @BeforeEach
    void setUp() {
        properties = new NacosEncryptProperties();
        testKey = ConfigCryptoUtils.generateKey();
        properties.setKey(testKey);
        properties.setEnabled(true);
        properties.setAlgorithm("AES");
        properties.setPrefix("ENC");

        environment = new StandardEnvironment();
    }

    @Test
    void testProcessWithEncryptedValue() {
        // 准备加密的配置值
        String plaintext = "secret-password";
        String encrypted = ConfigCryptoUtils.encrypt(plaintext, testKey);
        String encValue = "ENC(" + encrypted + ")";

        // 设置属性源
        Map<String, Object> props = new HashMap<>();
        props.put("database.password", encValue);
        props.put("database.username", "admin"); // 未加密的值

        MapPropertySource propertySource = new MapPropertySource("test", props);
        environment.getPropertySources().addFirst(propertySource);

        // 执行解密
        ConfigDecryptProcessor processor = new ConfigDecryptProcessor(properties);
        processor.process(environment);

        // 验证解密结果
        assertEquals(plaintext, environment.getProperty("database.password"));
        assertEquals("admin", environment.getProperty("database.username"));
    }

    @Test
    void testProcessWithMultipleEncryptedValues() {
        // 准备多个加密的配置值
        String password = ConfigCryptoUtils.encrypt("password123", testKey);
        String apiKey = ConfigCryptoUtils.encrypt("api-key-123", testKey);

        Map<String, Object> props = new HashMap<>();
        props.put("app.database.password", "ENC(" + password + ")");
        props.put("app.api.secret-key", "ENC(" + apiKey + ")");
        props.put("app.name", "MyApp");

        MapPropertySource propertySource = new MapPropertySource("test", props);
        environment.getPropertySources().addFirst(propertySource);

        ConfigDecryptProcessor processor = new ConfigDecryptProcessor(properties);
        processor.process(environment);

        assertEquals("password123", environment.getProperty("app.database.password"));
        assertEquals("api-key-123", environment.getProperty("app.api.secret-key"));
        assertEquals("MyApp", environment.getProperty("app.name"));
    }

    @Test
    void testProcessWithDisabled() {
        properties.setEnabled(false);

        Map<String, Object> props = new HashMap<>();
        String encrypted = ConfigCryptoUtils.encrypt("secret", testKey);
        props.put("test.key", "ENC(" + encrypted + ")");

        MapPropertySource propertySource = new MapPropertySource("test", props);
        environment.getPropertySources().addFirst(propertySource);

        ConfigDecryptProcessor processor = new ConfigDecryptProcessor(properties);
        processor.process(environment);

        // 禁用时不应该解密
        String value = environment.getProperty("test.key");
        assertTrue(value.startsWith("ENC("));
    }

    @Test
    void testProcessWithNoKey() {
        properties.setKey(null);

        Map<String, Object> props = new HashMap<>();
        props.put("test.key", "ENC(value)");

        MapPropertySource propertySource = new MapPropertySource("test", props);
        environment.getPropertySources().addFirst(propertySource);

        ConfigDecryptProcessor processor = new ConfigDecryptProcessor(properties);
        processor.process(environment);

        // 无密钥时不应该解密
        assertEquals("ENC(value)", environment.getProperty("test.key"));
    }

    @Test
    void testProcessWithEmptyKey() {
        properties.setKey("");

        Map<String, Object> props = new HashMap<>();
        props.put("test.key", "ENC(value)");

        MapPropertySource propertySource = new MapPropertySource("test", props);
        environment.getPropertySources().addFirst(propertySource);

        ConfigDecryptProcessor processor = new ConfigDecryptProcessor(properties);
        processor.process(environment);

        // 空密钥时不应该解密
        assertEquals("ENC(value)", environment.getProperty("test.key"));
    }

    @Test
    void testProcessWithNonEncryptedValues() {
        Map<String, Object> props = new HashMap<>();
        props.put("app.name", "MyApp");
        props.put("app.version", "1.0.0");
        props.put("server.port", "8080");

        MapPropertySource propertySource = new MapPropertySource("test", props);
        environment.getPropertySources().addFirst(propertySource);

        ConfigDecryptProcessor processor = new ConfigDecryptProcessor(properties);
        processor.process(environment);

        // 未加密的值应该保持不变
        assertEquals("MyApp", environment.getProperty("app.name"));
        assertEquals("1.0.0", environment.getProperty("app.version"));
        assertEquals("8080", environment.getProperty("server.port"));
    }

    @Test
    void testProcessWithCaseInsensitiveEnc() {
        String plaintext = "secret";
        String encrypted = ConfigCryptoUtils.encrypt(plaintext, testKey);

        Map<String, Object> props = new HashMap<>();
        props.put("test.key1", "ENC(" + encrypted + ")");
        props.put("test.key2", "enc(" + encrypted + ")"); // 小写
        props.put("test.key3", "Enc(" + encrypted + ")"); // 混合大小写

        MapPropertySource propertySource = new MapPropertySource("test", props);
        environment.getPropertySources().addFirst(propertySource);

        ConfigDecryptProcessor processor = new ConfigDecryptProcessor(properties);
        processor.process(environment);

        // 所有大小写变体都应该被解密
        assertEquals(plaintext, environment.getProperty("test.key1"));
        assertEquals(plaintext, environment.getProperty("test.key2"));
        assertEquals(plaintext, environment.getProperty("test.key3"));
    }

    @Test
    void testProcessWithInvalidEncryptedValue() {
        Map<String, Object> props = new HashMap<>();
        props.put("test.key", "ENC(invalid-encrypted-value)");

        MapPropertySource propertySource = new MapPropertySource("test", props);
        environment.getPropertySources().addFirst(propertySource);

        ConfigDecryptProcessor processor = new ConfigDecryptProcessor(properties);
        
        // 解密失败时不应该抛出异常，应该返回原值
        assertDoesNotThrow(() -> processor.process(environment));
        assertEquals("ENC(invalid-encrypted-value)", environment.getProperty("test.key"));
    }

    @Test
    void testProcessWithCustomPrefix() {
        properties.setPrefix("SECRET");

        String plaintext = "secret-value";
        String encrypted = ConfigCryptoUtils.encrypt(plaintext, testKey);

        Map<String, Object> props = new HashMap<>();
        props.put("test.key", "SECRET(" + encrypted + ")");

        MapPropertySource propertySource = new MapPropertySource("test", props);
        environment.getPropertySources().addFirst(propertySource);

        ConfigDecryptProcessor processor = new ConfigDecryptProcessor(properties);
        processor.process(environment);

        assertEquals(plaintext, environment.getProperty("test.key"));
    }

    @Test
    void testProcessWithCustomAlgorithm() {
        properties.setAlgorithm("AES");

        String plaintext = "test-value";
        String encrypted = ConfigCryptoUtils.encrypt(plaintext, testKey, "AES");

        Map<String, Object> props = new HashMap<>();
        props.put("test.key", "ENC(" + encrypted + ")");

        MapPropertySource propertySource = new MapPropertySource("test", props);
        environment.getPropertySources().addFirst(propertySource);

        ConfigDecryptProcessor processor = new ConfigDecryptProcessor(properties);
        processor.process(environment);

        assertEquals(plaintext, environment.getProperty("test.key"));
    }

    @Test
    void testProcessAvoidDuplicateProcessing() {
        String plaintext = "secret";
        String encrypted = ConfigCryptoUtils.encrypt(plaintext, testKey);

        Map<String, Object> props = new HashMap<>();
        props.put("test.key", "ENC(" + encrypted + ")");

        MapPropertySource propertySource = new MapPropertySource("test", props);
        environment.getPropertySources().addFirst(propertySource);

        ConfigDecryptProcessor processor = new ConfigDecryptProcessor(properties);
        
        // 第一次处理
        processor.process(environment);
        assertEquals(plaintext, environment.getProperty("test.key"));

        // 第二次处理应该被跳过
        processor.process(environment);
        assertEquals(plaintext, environment.getProperty("test.key"));
    }

    @Test
    void testProcessWithMixedContent() {
        // 包含加密和非加密值的混合内容
        String encrypted1 = ConfigCryptoUtils.encrypt("password1", testKey);
        String encrypted2 = ConfigCryptoUtils.encrypt("password2", testKey);

        Map<String, Object> props = new HashMap<>();
        props.put("db1.password", "ENC(" + encrypted1 + ")");
        props.put("db2.password", "ENC(" + encrypted2 + ")");
        props.put("db1.host", "localhost");
        props.put("db2.host", "remote-host");

        MapPropertySource propertySource = new MapPropertySource("test", props);
        environment.getPropertySources().addFirst(propertySource);

        ConfigDecryptProcessor processor = new ConfigDecryptProcessor(properties);
        processor.process(environment);

        assertEquals("password1", environment.getProperty("db1.password"));
        assertEquals("password2", environment.getProperty("db2.password"));
        assertEquals("localhost", environment.getProperty("db1.host"));
        assertEquals("remote-host", environment.getProperty("db2.host"));
    }
}
