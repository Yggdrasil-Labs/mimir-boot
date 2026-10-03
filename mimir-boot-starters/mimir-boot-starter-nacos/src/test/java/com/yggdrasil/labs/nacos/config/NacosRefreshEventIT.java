package com.yggdrasil.labs.nacos.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.autoconfigure.ConfigurationPropertiesRebinderAutoConfiguration;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.alibaba.cloud.nacos.configdata.NacosConfigRefreshEventListener;
import com.alibaba.cloud.nacos.refresh.NacosConfigRefreshEvent;
import com.yggdrasil.labs.nacos.crypto.ConfigCryptoUtils;

class NacosRefreshEventIT {

    @TempDir Path directory;

    @Test
    void shouldReloadDeletedPropertiesThroughNacosEventAndInjectedConsumer() throws IOException {
        String key = ConfigCryptoUtils.generateKey();
        writeSnapshot(1, encryptedProperties("old-secret", key));
        try (ConfigurableApplicationContext context = start(Map.of())) {
            Consumer consumer = context.getBean(Consumer.class);
            Settings proxy = consumer.settings;
            Lifecycle lifecycle = context.getBean(Lifecycle.class);
            assertEquals("old-secret", consumer.secret());
            assertEquals(12, consumer.timeout());
            assertEquals(1, lifecycle.initialized);

            writeSnapshot(2, Map.of());
            assertEquals("old-secret", context.getEnvironment().getProperty("app.secret"));
            refresh(context);

            assertEquals("2", context.getEnvironment().getProperty("td039.snapshot-version"));
            assertNull(context.getEnvironment().getProperty("app.secret"));
            assertEquals(1, lifecycle.destroyed);
            assertEquals(1, lifecycle.initialized);
            assertNull(consumer.secret());
            assertEquals(30, consumer.timeout());
            assertEquals(2, lifecycle.initialized);
            assertSame(consumer, context.getBean(Consumer.class));
            assertSame(proxy, consumer.settings);
            assertSame(proxy, context.getBean(Settings.class));
            assertTrue(lifecycle.changedKeys.get(0).contains("td039.snapshot-version"));
            assertTrue(lifecycle.changedKeys.get(0).contains("app.timeout"));

            writeSnapshot(3, encryptedProperties("restored-secret", key));
            refresh(context);

            assertEquals(2, lifecycle.destroyed);
            assertEquals("restored-secret", consumer.secret());
            assertEquals(3, lifecycle.initialized);
        }
    }

    @Test
    void shouldRebindFallbackAfterDeletionWithEncryptionStillConfigured() throws IOException {
        String key = ConfigCryptoUtils.generateKey();
        writeSnapshot(1, encryptedProperties("old-secret", key));
        try (ConfigurableApplicationContext context = start(Map.of("app.secret", "fallback"))) {
            Consumer consumer = context.getBean(Consumer.class);
            assertEquals("old-secret", consumer.secret());

            writeSnapshot(2, Map.of(NacosEncryptProperties.PREFIX + ".key", key));
            refresh(context);

            assertEquals("fallback", context.getEnvironment().getProperty("app.secret"));
            assertEquals("fallback", consumer.secret());
            assertEquals(30, consumer.timeout());
            assertEquals(1, context.getBean(Lifecycle.class).destroyed);
        }
    }

    @Test
    void shouldRetainLiveConfigurationWhenReloadDecryptionFailsAndRecover() throws IOException {
        String key = ConfigCryptoUtils.generateKey();
        writeSnapshot(1, encryptedProperties("old-secret", key));
        try (ConfigurableApplicationContext context = start(Map.of())) {
            Consumer consumer = context.getBean(Consumer.class);
            Lifecycle lifecycle = context.getBean(Lifecycle.class);
            assertEquals("old-secret", consumer.secret());
            Map<String, String> invalid =
                    Map.of(
                            NacosEncryptProperties.PREFIX + ".key",
                            ConfigCryptoUtils.generateKey(),
                            "app.secret",
                            encryptedProperties("next-secret", key).get("app.secret"));
            writeSnapshot(2, invalid);

            assertThrows(IllegalStateException.class, () -> refresh(context));

            assertEquals("1", context.getEnvironment().getProperty("td039.snapshot-version"));
            assertEquals("old-secret", consumer.secret());
            assertEquals(0, lifecycle.destroyed);
            assertEquals(1, lifecycle.initialized);
            assertTrue(lifecycle.changedKeys.isEmpty());

            writeSnapshot(3, encryptedProperties("next-secret", key));
            refresh(context);

            assertEquals("next-secret", consumer.secret());
            assertEquals(1, lifecycle.destroyed);
            assertEquals(2, lifecycle.initialized);
        }
    }

    private Map<String, String> encryptedProperties(String value, String key) {
        return Map.of(
                NacosEncryptProperties.PREFIX + ".key",
                key,
                "app.secret",
                "ENC(" + ConfigCryptoUtils.encrypt(value, key) + ")",
                "app.timeout",
                "12");
    }

    private void writeSnapshot(int version, Map<String, String> values) throws IOException {
        Properties properties = new Properties();
        properties.putAll(values);
        properties.setProperty("td039.snapshot-version", Integer.toString(version));
        try (Writer writer = Files.newBufferedWriter(directory.resolve("application.properties"))) {
            properties.store(writer, null);
        }
    }

    private ConfigurableApplicationContext start(Map<String, String> fallback) {
        Properties defaults = new Properties();
        defaults.putAll(fallback);
        defaults.setProperty("spring.cloud.nacos.config.import-check.enabled", "false");
        defaults.setProperty(
                "spring.config.location",
                directory.resolve("application.properties").toUri().toString());
        SpringApplication application = new SpringApplication(EventConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(defaults);
        return application.run();
    }

    private void refresh(ConfigurableApplicationContext context) {
        context.publishEvent(new NacosConfigRefreshEvent(context, null, "TD-039 配置刷新"));
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({
        NacosEncryptAutoConfiguration.class,
        RefreshAutoConfiguration.class,
        ConfigurationPropertiesRebinderAutoConfiguration.class
    })
    static class EventConfiguration {

        @Bean
        NacosConfigRefreshEventListener nacosConfigSpringCloudRefreshEventListener() {
            return new NacosConfigRefreshEventListener();
        }

        @Bean
        Lifecycle lifecycle() {
            return new Lifecycle();
        }

        @Bean
        ApplicationListener<EnvironmentChangeEvent> environmentChanges(Lifecycle lifecycle) {
            return event -> lifecycle.changedKeys.add(Set.copyOf(event.getKeys()));
        }

        @Bean(initMethod = "initialize", destroyMethod = "destroy")
        @RefreshScope
        @ConfigurationProperties("app")
        Settings settings(Lifecycle lifecycle) {
            return new Settings(lifecycle);
        }

        @Bean
        Consumer consumer(Settings settings) {
            return new Consumer(settings);
        }
    }

    static class Lifecycle {
        private int initialized;
        private int destroyed;
        private final List<Set<String>> changedKeys = new ArrayList<>();
    }

    static class Consumer {
        private final Settings settings;

        Consumer(Settings settings) {
            this.settings = settings;
        }

        String secret() {
            return settings.getSecret();
        }

        int timeout() {
            return settings.getTimeout();
        }
    }

    public static class Settings {
        private final Lifecycle lifecycle;
        private String secret;
        private int timeout = 30;

        Settings(Lifecycle lifecycle) {
            this.lifecycle = lifecycle;
        }

        public void initialize() {
            lifecycle.initialized++;
        }

        public void destroy() {
            lifecycle.destroyed++;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public int getTimeout() {
            return timeout;
        }

        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }
    }
}
