package com.yggdrasil.labs.nacos.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.cloud.autoconfigure.ConfigurationPropertiesRebinderAutoConfiguration;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertySource;

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
            assertTrue(lifecycle.changedKeys.get(0).contains("app.secret"));

            writeSnapshot(3, encryptedProperties("restored-secret", key));
            refresh(context);

            assertEquals(2, lifecycle.destroyed);
            assertEquals("restored-secret", consumer.secret());
            assertEquals(3, lifecycle.initialized);
        }
    }

    @Test
    void shouldExposeKnownLimitationForDisappearedSourceAndRecoverAfterExplicitRemoval()
            throws IOException {
        String key = ConfigCryptoUtils.generateKey();
        writeSnapshot(1, encryptedProperties("old-secret", key));
        try (ConfigurableApplicationContext context = start(Map.of())) {
            Consumer consumer = context.getBean(Consumer.class);
            Settings proxy = consumer.settings;
            Lifecycle lifecycle = context.getBean(Lifecycle.class);
            PropertySource<?> rawSource =
                    context.getEnvironment().getPropertySources().stream()
                            .filter(source -> source instanceof OriginTrackedMapPropertySource)
                            .filter(
                                    source ->
                                            "1"
                                                    .equals(
                                                            source.getProperty(
                                                                    "td039.snapshot-version")))
                            .findFirst()
                            .orElseThrow();
            assertEquals("old-secret", consumer.secret());
            assertEquals(12, consumer.timeout());

            // 不保留版本标记，让空文件重载时整个 ConfigData 属性源消失。
            Files.writeString(directory.resolve("application.properties"), "");
            refresh(context);

            // 已知限制：完整刷新仍保留旧原始源，重建目标也继续绑定旧值。
            assertEquals("1", context.getEnvironment().getProperty("td039.snapshot-version"));
            assertEquals("old-secret", context.getEnvironment().getProperty("app.secret"));
            assertNotNull(context.getEnvironment().getPropertySources().get(rawSource.getName()));
            assertEquals(1, lifecycle.changedKeys.size());
            assertEquals(1, lifecycle.destroyed);
            assertEquals(1, lifecycle.initialized);
            assertEquals("old-secret", consumer.secret());
            assertEquals(12, consumer.timeout());
            assertEquals(2, lifecycle.initialized);

            // 仅清理此测试夹具的源，验证实时 Environment 确实删键后的恢复条件。
            assertNotNull(
                    context.getEnvironment().getPropertySources().remove(rawSource.getName()));
            assertNotNull(
                    context.getEnvironment()
                            .getPropertySources()
                            .remove("decryptedProperties:" + rawSource.getName()));
            assertNull(context.getEnvironment().getProperty("app.secret"));
            refresh(context);

            assertNull(context.getEnvironment().getProperty("td039.snapshot-version"));
            assertNull(context.getEnvironment().getProperty("app.secret"));
            assertEquals(2, lifecycle.destroyed);
            assertEquals(2, lifecycle.initialized);
            assertNull(consumer.secret());
            assertEquals(30, consumer.timeout());
            assertEquals(3, lifecycle.initialized);
            assertSame(proxy, consumer.settings);
            assertSame(proxy, context.getBean(Settings.class));
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

    @Test
    void shouldRestoreSourceReferencesWhenMergedApplicationSourceCannotDecrypt()
            throws IOException {
        String oldKey = ConfigCryptoUtils.generateKey();
        writeSnapshot(1, encryptedProperties("old-secret", oldKey));
        try (ConfigurableApplicationContext context = start(Map.of())) {
            Consumer consumer = context.getBean(Consumer.class);
            Lifecycle lifecycle = context.getBean(Lifecycle.class);
            assertEquals("old-secret", consumer.secret());
            context.getEnvironment()
                    .getPropertySources()
                    .addLast(
                            new org.springframework.core.env.MapPropertySource(
                                    "application-extension",
                                    Map.of(
                                            "app.extension-secret",
                                            "ENC("
                                                    + ConfigCryptoUtils.encrypt("extension", oldKey)
                                                    + ")")));
            context.getBean(NacosEncryptAutoConfiguration.class)
                    .processDecrypt(context.getEnvironment());
            List<PropertySource<?>> previous =
                    context.getEnvironment().getPropertySources().stream().toList();
            String nextKey = ConfigCryptoUtils.generateKey();
            writeSnapshot(2, encryptedProperties("next-secret", nextKey));

            assertThrows(IllegalStateException.class, () -> refresh(context));

            List<PropertySource<?>> current =
                    context.getEnvironment().getPropertySources().stream().toList();
            assertEquals(previous.size(), current.size());
            for (int i = 0; i < previous.size(); i++) {
                assertSame(previous.get(i), current.get(i));
            }
            assertEquals("1", context.getEnvironment().getProperty("td039.snapshot-version"));
            assertEquals("extension", context.getEnvironment().getProperty("app.extension-secret"));
            assertEquals("old-secret", consumer.secret());
            assertEquals(0, lifecycle.destroyed);
            assertEquals(1, lifecycle.initialized);
            assertTrue(lifecycle.changedKeys.isEmpty());

            context.getEnvironment().getPropertySources().remove("application-extension");
            refresh(context);

            assertEquals("next-secret", consumer.secret());
            assertNull(context.getEnvironment().getProperty("app.extension-secret"));
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
        NacosRefreshAutoConfiguration.class,
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
