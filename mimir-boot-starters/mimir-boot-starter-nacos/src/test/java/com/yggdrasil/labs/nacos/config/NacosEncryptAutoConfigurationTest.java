package com.yggdrasil.labs.nacos.config;

import com.yggdrasil.labs.nacos.crypto.ConfigCryptoUtils;
import com.yggdrasil.labs.test.base.BaseIntegrationTest;
import com.yggdrasil.labs.test.base.BaseUnitTest;
import com.yggdrasil.labs.test.util.AssertUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Nacos 配置加密自动配置测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class NacosEncryptAutoConfigurationTest extends BaseUnitTest {

    private NacosEncryptProperties properties;
    private StandardEnvironment environment;
    private NacosEncryptAutoConfiguration configuration;
    private String testKey;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();
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
        AssertUtils.assertEquals(plaintext, environment.getProperty("app.secret"));
    }

    @Test
    void shouldDecryptPropertyBeforeBeanCreation() {
        String plaintext = "startup-secret";
        String encrypted = ConfigCryptoUtils.encrypt(plaintext, testKey);
        SpringApplication application = new SpringApplication(StartupTestConfiguration.class);
        application.setDefaultProperties(Map.of(
                "mimir.boot.nacos.encrypt.key", testKey,
                "app.secret", "ENC(" + encrypted + ")",
                "spring.cloud.nacos.config.import-check.enabled", "false"
        ));

        try (ConfigurableApplicationContext context = application.run()) {
            assertEquals(plaintext, context.getBean("startupSecret", String.class));
        }
    }

    @Test
    void shouldDecryptPropertyWithLegacyPrefixDuringMigration() {
        String plaintext = "legacy-startup-secret";
        String encrypted = ConfigCryptoUtils.encrypt(plaintext, testKey);
        SpringApplication application = new SpringApplication(StartupTestConfiguration.class);
        application.setDefaultProperties(Map.of(
                "mimir.nacos.encrypt.key", testKey,
                "app.secret", "ENC(" + encrypted + ")",
                "spring.cloud.nacos.config.import-check.enabled", "false"
        ));

        try (ConfigurableApplicationContext context = application.run()) {
            assertEquals(plaintext, context.getBean("startupSecret", String.class));
        }
    }

    @Test
    void shouldPreferCurrentPrefixWhenBothPrefixesAreConfigured() {
        String legacyKey = ConfigCryptoUtils.generateKey();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "mimir.boot.nacos.encrypt.key", testKey,
                "mimir.nacos.encrypt.key", legacyKey,
                "app.secret", "ENC(" + ConfigCryptoUtils.encrypt("current-secret", testKey) + ")"
        )));

        configuration.processDecrypt(environment);

        assertEquals("current-secret", environment.getProperty("app.secret"));
    }

    @Test
    void shouldNotFallbackToLegacyPrefixWhenCurrentPrefixIsIncomplete() {
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "mimir.boot.nacos.encrypt.enabled", "true",
                "mimir.nacos.encrypt.key", testKey,
                "app.secret", "ENC(invalid-ciphertext)"
        )));

        assertThrows(IllegalStateException.class, () -> configuration.processDecrypt(environment));
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
    void shouldFailWhenEncryptedValueHasNoKey() {
        properties.setKey(null);

        Map<String, Object> props = new HashMap<>();
        props.put("app.secret", "ENC(value)");

        MapPropertySource propertySource = new MapPropertySource("test", props);
        environment.getPropertySources().addFirst(propertySource);

        assertThrows(IllegalStateException.class, () -> configuration.processDecrypt(environment));
    }

    @Test
    void shouldFailWhenEncryptedValueHasEmptyKey() {
        properties.setKey("");

        Map<String, Object> props = new HashMap<>();
        props.put("app.secret", "ENC(value)");

        MapPropertySource propertySource = new MapPropertySource("test", props);
        environment.getPropertySources().addFirst(propertySource);

        assertThrows(IllegalStateException.class, () -> configuration.processDecrypt(environment));
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

        AssertUtils.assertEquals("secret1", environment.getProperty("app.secret1"));
        AssertUtils.assertEquals("secret2", environment.getProperty("app.secret2"));
        AssertUtils.assertEquals("MyApp", environment.getProperty("app.name"));
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
        AssertUtils.assertEquals("MyApp", environment.getProperty("app.name"));
        AssertUtils.assertEquals("1.0.0", environment.getProperty("app.version"));
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
        AssertUtils.assertEquals(plaintext, environment.getProperty("app.secret"));

        // 第二次处理应该被跳过
        configuration.processDecrypt(environment);
        AssertUtils.assertEquals(plaintext, environment.getProperty("app.secret"));
    }

    @Test
    void shouldRefreshDecryptedPropertyAfterEnvironmentChange() {
        Map<String, Object> props = new HashMap<>();
        props.put("app.secret", "ENC(" + ConfigCryptoUtils.encrypt("first-secret", testKey) + ")");
        environment.getPropertySources().addFirst(new MapPropertySource("nacos", props));
        configuration.processDecrypt(environment);

        props.put("app.secret", "ENC(" + ConfigCryptoUtils.encrypt("refreshed-secret", testKey) + ")");
        ApplicationContext context = mock(ApplicationContext.class);
        when(context.getEnvironment()).thenReturn(environment);
        configuration.setApplicationContext(context);

        configuration.onEnvironmentChange(new EnvironmentChangeEvent(Set.of("app.secret")));

        assertEquals("refreshed-secret", environment.getProperty("app.secret"));
    }

    @Test
    void shouldRemoveDecryptedValueWhenRefreshedPropertyBecomesPlaintext() {
        Map<String, Object> props = new HashMap<>();
        props.put("app.secret", "ENC(" + ConfigCryptoUtils.encrypt("first-secret", testKey) + ")");
        environment.getPropertySources().addFirst(new MapPropertySource("nacos", props));
        configuration.processDecrypt(environment);

        props.put("app.secret", "refreshed-plaintext");
        configuration.processDecrypt(environment);

        assertEquals("refreshed-plaintext", environment.getProperty("app.secret"));
    }

    @Test
    void shouldRemoveDecryptedValueWhenDecryptionIsDisabledAtRefresh() {
        Map<String, Object> props = new HashMap<>();
        props.put("app.secret", "ENC(" + ConfigCryptoUtils.encrypt("first-secret", testKey) + ")");
        environment.getPropertySources().addFirst(new MapPropertySource("nacos", props));
        configuration.processDecrypt(environment);

        properties.setEnabled(false);
        configuration.processDecrypt(environment);

        assertTrue(environment.getProperty("app.secret").startsWith("ENC("));
    }

    @Test
    void shouldKeepLastValidDecryptedValueWhenStrictRefreshFails() {
        Map<String, Object> props = new HashMap<>();
        props.put("app.secret", "ENC(" + ConfigCryptoUtils.encrypt("first-secret", testKey) + ")");
        environment.getPropertySources().addFirst(new MapPropertySource("nacos", props));
        configuration.processDecrypt(environment);

        props.put("app.secret", "ENC(invalid-ciphertext)");

        assertThrows(IllegalStateException.class, () -> configuration.processDecrypt(environment));
        assertEquals("first-secret", environment.getProperty("app.secret"));
    }

    @Configuration(proxyBeanMethods = false)
    static class StartupTestConfiguration {

        @Bean
        String startupSecret(@Value("${app.secret}") String secret) {
            return secret;
        }
    }
}
