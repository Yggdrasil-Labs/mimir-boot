package com.yggdrasil.labs.nacos.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.autoconfigure.ConfigurationPropertiesRebinderAutoConfiguration;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import com.yggdrasil.labs.nacos.crypto.ConfigCryptoUtils;

class NacosRefreshScopeIT {

    @Test
    void shouldRestoreMissingPropertiesToInitialValuesAfterFullRefresh() {
        String key = ConfigCryptoUtils.generateKey();
        MutableContext fixture = start(key);
        try (ConfigurableApplicationContext context = fixture.context()) {
            Settings settings = context.getBean(Settings.class);
            Settings previous = context.getBean("scopedTarget.settings", Settings.class);
            PlainSettings plain = context.getBean(PlainSettings.class);
            assertEquals("old-secret", settings.getSecret());
            assertEquals(12, settings.getTimeout());
            assertEquals(List.of("first", "second"), settings.getTags());

            removeApplicationProperties(fixture);
            fixture.values().remove(NacosEncryptProperties.PREFIX + ".key");
            assertEquals(
                    NacosConfigDataContextRefresher.class,
                    context.getBean(ContextRefresher.class).getClass());
            context.getBean(ContextRefresher.class).refresh();

            assertNull(context.getEnvironment().getProperty("app.secret"));
            assertNull(settings.getSecret());
            assertEquals(30, settings.getTimeout());
            assertEquals(List.of(), settings.getTags());
            assertSame(settings, context.getBean(Settings.class));
            assertNotSame(previous, context.getBean("scopedTarget.settings", Settings.class));
            assertSame(plain, context.getBean(PlainSettings.class));
            assertEquals("old-secret", plain.getSecret());
        }
    }

    @Test
    void shouldUseFallbackRestoreEncryptionAndDeleteAgainWithEncryptionStillEnabled() {
        String key = ConfigCryptoUtils.generateKey();
        MutableContext fixture = start(key);
        try (ConfigurableApplicationContext context = fixture.context()) {
            Settings settings = context.getBean(Settings.class);
            assertEquals("old-secret", settings.getSecret());
            context.getEnvironment()
                    .getPropertySources()
                    .addLast(new MapPropertySource("fallback", Map.of("app.secret", "fallback")));
            removeApplicationProperties(fixture);
            fixture.values().remove(NacosEncryptProperties.PREFIX + ".key");

            context.getBean(ContextRefresher.class).refresh();

            assertEquals("fallback", settings.getSecret());
            assertNull(
                    context.getEnvironment()
                            .getPropertySources()
                            .get("decryptedProperties:nacos-refresh"));

            fixture.values().put(NacosEncryptProperties.PREFIX + ".key", key);
            fixture.values().put("app.secret", encrypted("restored-secret", key));
            context.getBean(ContextRefresher.class).refresh();

            assertEquals("restored-secret", settings.getSecret());
            assertEquals("restored-secret", context.getEnvironment().getProperty("app.secret"));
            context.getEnvironment().getPropertySources().remove("fallback");
            fixture.values().remove("app.secret");
            context.getBean(ContextRefresher.class).refresh();

            assertNull(settings.getSecret());
            assertNull(context.getEnvironment().getProperty("app.secret"));
            assertEquals(30, settings.getTimeout());
            assertEquals(List.of(), settings.getTags());
        }
    }

    @Test
    void shouldRetainExistingTargetAfterFailedDecryptionAndRecoverOnNextRefresh() {
        String key = ConfigCryptoUtils.generateKey();
        MutableContext fixture = start(key);
        try (ConfigurableApplicationContext context = fixture.context()) {
            Settings settings = context.getBean(Settings.class);
            assertEquals("old-secret", settings.getSecret());
            Settings previous = context.getBean("scopedTarget.settings", Settings.class);
            fixture.values()
                    .put(NacosEncryptProperties.PREFIX + ".key", ConfigCryptoUtils.generateKey());
            fixture.values().put("app.secret", encrypted("next-secret", key));

            assertThrows(
                    IllegalStateException.class,
                    () -> context.getBean(ContextRefresher.class).refresh());

            assertSame(previous, context.getBean("scopedTarget.settings", Settings.class));
            assertEquals("old-secret", settings.getSecret());
            assertEquals("old-secret", context.getEnvironment().getProperty("app.secret"));

            fixture.values().put(NacosEncryptProperties.PREFIX + ".key", key);
            context.getBean(ContextRefresher.class).refresh();

            assertEquals("next-secret", settings.getSecret());
            assertNotSame(previous, context.getBean("scopedTarget.settings", Settings.class));
        }
    }

    @Test
    void shouldRequireFullRefreshToInvalidateScopedTarget() {
        String key = ConfigCryptoUtils.generateKey();
        MutableContext fixture = start(key);
        try (ConfigurableApplicationContext context = fixture.context()) {
            Settings settings = context.getBean(Settings.class);
            assertEquals("old-secret", settings.getSecret());
            Settings previous = context.getBean("scopedTarget.settings", Settings.class);
            removeApplicationProperties(fixture);
            fixture.values().remove(NacosEncryptProperties.PREFIX + ".key");

            context.publishEvent(new EnvironmentChangeEvent(Set.of("app.secret")));

            assertNull(context.getEnvironment().getProperty("app.secret"));
            assertSame(previous, context.getBean("scopedTarget.settings", Settings.class));
            assertEquals("old-secret", settings.getSecret());

            context.getBean(ContextRefresher.class).refresh();

            assertNull(settings.getSecret());
            assertNotSame(previous, context.getBean("scopedTarget.settings", Settings.class));
        }
    }

    private void removeApplicationProperties(MutableContext fixture) {
        fixture.values().remove("app.secret");
        fixture.values().remove("app.timeout");
        fixture.values().remove("app.tags");
    }

    private String encrypted(String plaintext, String key) {
        return "ENC(" + ConfigCryptoUtils.encrypt(plaintext, key) + ")";
    }

    private MutableContext start(String key) {
        Map<String, Object> values = new HashMap<>();
        values.put(NacosEncryptProperties.PREFIX + ".key", key);
        values.put("app.secret", encrypted("old-secret", key));
        values.put("app.timeout", 12);
        values.put("app.tags", "first,second");
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("nacos-refresh", values));
        SpringApplication application = new SpringApplication(ScopedConfiguration.class);
        application.setEnvironment(environment);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(
                Map.of("spring.cloud.nacos.config.import-check.enabled", "false"));
        return new MutableContext(application.run(), values);
    }

    private record MutableContext(
            ConfigurableApplicationContext context, Map<String, Object> values) {}

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({
        NacosEncryptAutoConfiguration.class,
        NacosRefreshAutoConfiguration.class,
        RefreshAutoConfiguration.class,
        ConfigurationPropertiesRebinderAutoConfiguration.class
    })
    static class ScopedConfiguration {

        @Bean
        @RefreshScope
        @ConfigurationProperties("app")
        Settings settings() {
            return new Settings();
        }

        @Bean
        @ConfigurationProperties("app")
        PlainSettings plainSettings() {
            return new PlainSettings();
        }
    }

    public static class Settings {

        private String secret;
        private int timeout = 30;
        private List<String> tags = new ArrayList<>();

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

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }
    }

    public static class PlainSettings {

        private String secret;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }
}
