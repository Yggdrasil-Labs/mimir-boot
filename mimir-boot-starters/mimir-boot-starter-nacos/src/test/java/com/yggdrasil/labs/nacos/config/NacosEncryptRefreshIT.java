package com.yggdrasil.labs.nacos.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.yggdrasil.labs.nacos.crypto.ConfigCryptoUtils;
import com.yggdrasil.labs.nacos.decrypt.ConfigDecryptProcessor;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.autoconfigure.ConfigurationPropertiesRebinderAutoConfiguration;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NacosEncryptRefreshIT {

    private static final String PROPERTY_NAME = "app.secret";

    @Test
    void shouldRefreshEnvironmentAndBoundBean() {
        String key = ConfigCryptoUtils.generateKey();
        MutableRefreshContext refreshContext = startWithEncryptedSecret(key, "old-plaintext");
        try (ConfigurableApplicationContext context = refreshContext.context()) {
            assertCurrentSecret(context, "old-plaintext");

            refreshContext.properties().put(PROPERTY_NAME, encrypted("new-plaintext", key));
            context.publishEvent(new EnvironmentChangeEvent(Set.of(PROPERTY_NAME)));

            assertCurrentSecret(context, "new-plaintext");
        }
    }

    @Test
    void shouldKeepPreviousValuesWhenRefreshedKeyIsInvalid() {
        String validKey = ConfigCryptoUtils.generateKey();
        String invalidKey = ConfigCryptoUtils.generateKey();
        MutableRefreshContext refreshContext = startWithEncryptedSecret(validKey, "old-plaintext");
        try (ConfigurableApplicationContext context = refreshContext.context()) {
            assertCurrentSecret(context, "old-plaintext");
            refreshContext.properties().put(NacosEncryptProperties.PREFIX + ".key", invalidKey);
            refreshContext.properties().put(PROPERTY_NAME, encrypted("new-plaintext", validKey));

            assertThrows(IllegalStateException.class,
                    () -> context.publishEvent(new EnvironmentChangeEvent(Set.of(PROPERTY_NAME))));

            assertCurrentSecret(context, "old-plaintext");
        }
    }

    @Test
    void shouldNotLogSensitiveRefreshMaterialsWhenRefreshedKeyIsInvalid() {
        String validKey = ConfigCryptoUtils.generateKey();
        String invalidKey = ConfigCryptoUtils.generateKey();
        String nextPlaintext = "new-plaintext";
        String nextCiphertext = ConfigCryptoUtils.encrypt(nextPlaintext, validKey);
        MutableRefreshContext refreshContext = startWithEncryptedSecret(validKey, "old-plaintext");
        Logger logger = (Logger) LoggerFactory.getLogger(ConfigDecryptProcessor.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try (ConfigurableApplicationContext context = refreshContext.context()) {
            refreshContext.properties().put(NacosEncryptProperties.PREFIX + ".key", invalidKey);
            refreshContext.properties().put(PROPERTY_NAME, "ENC(" + nextCiphertext + ")");

            assertThrows(IllegalStateException.class,
                    () -> context.publishEvent(new EnvironmentChangeEvent(Set.of(PROPERTY_NAME))));

            String messages = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (left, right) -> left + "\n" + right);
            assertTrue(!messages.contains(validKey)
                    && !messages.contains(invalidKey)
                    && !messages.contains(nextCiphertext)
                    && !messages.contains(nextPlaintext));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private MutableRefreshContext startWithEncryptedSecret(String key, String plaintext) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(NacosEncryptProperties.PREFIX + ".key", key);
        properties.put(PROPERTY_NAME, encrypted(plaintext, key));
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("nacos-refresh", properties));

        SpringApplication application = new SpringApplication(RefreshConfiguration.class);
        application.setEnvironment(environment);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(Map.of("spring.cloud.nacos.config.import-check.enabled", "false"));
        return new MutableRefreshContext(application.run(), properties);
    }

    private void assertCurrentSecret(ConfigurableApplicationContext context, String expected) {
        assertEquals(expected, context.getEnvironment().getProperty(PROPERTY_NAME));
        assertEquals(expected, context.getBean(RefreshProperties.class).getSecret());
    }

    private String encrypted(String plaintext, String key) {
        return "ENC(" + ConfigCryptoUtils.encrypt(plaintext, key) + ")";
    }

    private record MutableRefreshContext(ConfigurableApplicationContext context, Map<String, Object> properties) {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RefreshProperties.class)
    @ImportAutoConfiguration({
            NacosEncryptAutoConfiguration.class,
            ConfigurationPropertiesRebinderAutoConfiguration.class
    })
    static class RefreshConfiguration {
    }

    @ConfigurationProperties("app")
    static class RefreshProperties {

        private String secret;

        String getSecret() {
            return secret;
        }

        void setSecret(String secret) {
            this.secret = secret;
        }
    }
}
