package com.yggdrasil.labs.nacos.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import javax.crypto.AEADBadTagException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.yggdrasil.labs.nacos.crypto.ConfigCryptoUtils;

/** 仅通过 nacos-live profile 启用，验证真实服务推送和消费者代理。 */
class NacosLiveRefreshIT {

    private static final Duration REFRESH_TIMEOUT = Duration.ofSeconds(30);

    @Test
    void shouldRefreshRealRemoteChangesAndPreserveConsumersWhenDecryptionFails() throws Exception {
        Properties connection = connectionProperties();
        String run = UUID.randomUUID().toString();
        String group = "MIMIR_LIVE_" + run;
        String dataId = "mimir-live-" + run + ".properties";
        String key = ConfigCryptoUtils.generateKey();
        ConfigService publisher = NacosFactory.createConfigService(connection);
        ConfigService consumerService = null;
        try {
            publish(publisher, dataId, group, encryptedSnapshot("1", "initial-secret", key));
            try (ConfigurableApplicationContext context = start(connection, dataId, group)) {
                consumerService = context.getBean(NacosConfigManager.class).getConfigService();
                Consumer consumer = context.getBean(Consumer.class);
                Settings requiredProxy = consumer.required;
                Settings fallbackProxy = consumer.fallback;
                Lifecycle lifecycle = context.getBean(Lifecycle.class);
                assertEquals("initial-secret", consumer.requiredSecret());
                assertEquals("initial-secret", consumer.fallbackSecret());
                assertEquals(12, consumer.requiredTimeout());
                String remainingEncrypted =
                        "ENC(" + ConfigCryptoUtils.encrypt("initial-secret", key) + ")";
                String deletedKey =
                        propertiesSnapshot(
                                "2",
                                Map.of(
                                        NacosEncryptProperties.PREFIX + ".key",
                                        key,
                                        "live.fallback.secret",
                                        remainingEncrypted,
                                        "live.fallback.timeout",
                                        "12"));
                refreshAndAssertRecreated(
                        context,
                        lifecycle,
                        "2",
                        () -> publish(publisher, dataId, group, deletedKey),
                        () -> {
                            assertNull(
                                    context.getEnvironment().getProperty("live.required.secret"));
                            assertNull(consumer.requiredSecret());
                            assertEquals("initial-secret", consumer.fallbackSecret());
                            assertEquals(30, consumer.requiredTimeout());
                        });
                assertChangedKeys(lifecycle, "live.required.secret", "live.required.timeout");

                String deletedPrefix =
                        propertiesSnapshot(
                                "3",
                                Map.of(
                                        "live.required.secret",
                                        "plain-secret",
                                        "live.fallback.secret",
                                        "plain-secret"));
                refreshAndAssertRecreated(
                        context,
                        lifecycle,
                        "3",
                        () -> publish(publisher, dataId, group, deletedPrefix),
                        () -> assertSecrets(context, consumer, "plain-secret"));
                assertNull(
                        context.getEnvironment()
                                .getProperty(NacosEncryptProperties.PREFIX + ".key"));
                assertFalse(hasDecryptedSources(context));
                assertChangedKeys(lifecycle, NacosEncryptProperties.PREFIX + ".key");

                refreshAndAssertRecreated(
                        context,
                        lifecycle,
                        "4",
                        () ->
                                publish(
                                        publisher,
                                        dataId,
                                        group,
                                        encryptedSnapshot("4", "restored-secret", key)),
                        () -> assertSecrets(context, consumer, "restored-secret"));

                // SDK 拒绝空字符串；仅注释的 properties 不产生任何配置键或原始源。
                refreshAndAssertRecreated(
                        context,
                        lifecycle,
                        null,
                        () -> publish(publisher, dataId, group, "# 本轮配置已清空\n"),
                        () -> assertInitialValues(context, consumer));
                assertChangedKeys(
                        lifecycle,
                        "live.required.secret",
                        "live.required.timeout",
                        "live.snapshot-version");

                refreshAndAssertRecreated(
                        context,
                        lifecycle,
                        "5",
                        () ->
                                publish(
                                        publisher,
                                        dataId,
                                        group,
                                        encryptedSnapshot("5", "before-delete", key)),
                        () -> assertSecrets(context, consumer, "before-delete"));
                refreshAndAssertRecreated(
                        context,
                        lifecycle,
                        null,
                        () -> {
                            assertTrue(publisher.removeConfig(dataId, group), "删除本轮独立 DataId 失败");
                            assertNull(publisher.getConfig(dataId, group, 5000), "本轮 DataId 未删除");
                        },
                        () -> assertInitialValues(context, consumer));
                assertChangedKeys(
                        lifecycle,
                        "live.required.secret",
                        "live.required.timeout",
                        "live.snapshot-version");

                refreshAndAssertRecreated(
                        context,
                        lifecycle,
                        "6",
                        () ->
                                publish(
                                        publisher,
                                        dataId,
                                        group,
                                        encryptedSnapshot("6", "stable-secret", key)),
                        () -> assertSecrets(context, consumer, "stable-secret"));
                // 版本标记变化保证服务器产生新推送，业务值保持一致。
                refreshAndAssertRecreated(
                        context,
                        lifecycle,
                        "7",
                        () ->
                                publish(
                                        publisher,
                                        dataId,
                                        group,
                                        encryptedSnapshot("7", "stable-secret", key)),
                        () -> assertSecrets(context, consumer, "stable-secret"));
                assertChangedKeys(lifecycle, "live.snapshot-version");

                RefreshState beforeFailure = refreshState(context, lifecycle);
                publish(
                        publisher,
                        dataId,
                        group,
                        encryptedSnapshot(
                                "8", "invalid-secret", key, ConfigCryptoUtils.generateKey()));
                await(
                        () -> lifecycle.failures.size() > beforeFailure.failures(),
                        "未观察到真实推送完成的解密失败");
                assertCurrentDecryptionFailure(
                        lifecycle,
                        beforeFailure.failures(),
                        Set.of("live.required.secret", "live.fallback.secret"));
                assertFailedRefreshKeepsSourcesAndTargets(context, lifecycle, beforeFailure);
                assertSecrets(context, consumer, "stable-secret");
                assertEquals("7", context.getEnvironment().getProperty("live.snapshot-version"));

                refreshAndAssertRecreated(
                        context,
                        lifecycle,
                        "9",
                        () ->
                                publish(
                                        publisher,
                                        dataId,
                                        group,
                                        encryptedSnapshot("9", "recovered-secret", key)),
                        () -> assertSecrets(context, consumer, "recovered-secret"));
                assertSame(requiredProxy, consumer.required);
                assertSame(fallbackProxy, consumer.fallback);
                assertSame(consumer, context.getBean(Consumer.class));
            }
        } finally {
            try {
                assertTrue(publisher.removeConfig(dataId, group), "清理本轮独立配置失败");
                assertNull(publisher.getConfig(dataId, group, 5000), "本轮独立配置仍存在");
            } finally {
                try {
                    if (consumerService != null) {
                        consumerService.shutDown();
                    }
                } finally {
                    publisher.shutDown();
                }
            }
        }
    }

    private static Properties connectionProperties() {
        Properties properties = new Properties();
        properties.setProperty("serverAddr", requiredEnvironment("NACOS_TEST_SERVER_ADDR"));
        properties.setProperty("username", requiredEnvironment("NACOS_TEST_USERNAME"));
        properties.setProperty("password", requiredEnvironment("NACOS_TEST_PASSWORD"));
        String namespace = System.getenv("NACOS_TEST_NAMESPACE");
        if (namespace != null && !namespace.isBlank()) {
            properties.setProperty("namespace", namespace);
        }
        return properties;
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("nacos-live 必须配置环境变量 " + name);
        }
        return value;
    }

    private static String encryptedSnapshot(String version, String secret, String key)
            throws Exception {
        return encryptedSnapshot(version, secret, key, key);
    }

    private static String encryptedSnapshot(
            String version, String secret, String encryptionKey, String configuredKey)
            throws Exception {
        String encrypted = "ENC(" + ConfigCryptoUtils.encrypt(secret, encryptionKey) + ")";
        return propertiesSnapshot(
                version,
                Map.of(
                        NacosEncryptProperties.PREFIX + ".key",
                        configuredKey,
                        "live.required.secret",
                        encrypted,
                        "live.required.timeout",
                        "12",
                        "live.fallback.secret",
                        encrypted,
                        "live.fallback.timeout",
                        "12"));
    }

    private static String propertiesSnapshot(String version, Map<String, String> snapshot)
            throws Exception {
        Properties values = new Properties();
        values.setProperty("live.snapshot-version", version);
        values.putAll(snapshot);
        StringWriter writer = new StringWriter();
        values.store(writer, null);
        return writer.toString();
    }

    private static void publish(
            ConfigService publisher, String dataId, String group, String content) throws Exception {
        assertTrue(publisher.publishConfig(dataId, group, content, "properties"), "发布本轮独立配置失败");
        assertTrue(content.equals(publisher.getConfig(dataId, group, 5000)), "服务端内容与本轮推送不一致");
    }

    private static ConfigurableApplicationContext start(
            Properties connection, String dataId, String group) {
        Properties defaults = new Properties();
        connection.forEach(
                (name, value) -> {
                    String property = "serverAddr".equals(name) ? "server-addr" : name.toString();
                    defaults.setProperty("spring.cloud.nacos.config." + property, value.toString());
                });
        defaults.setProperty("spring.application.name", "mimir-nacos-live-consumer");
        defaults.setProperty(
                "spring.config.location", "optional:classpath:/nacos-live-test.properties");
        defaults.setProperty(
                "spring.config.import",
                "nacos:" + dataId + "?group=" + group + "&refreshEnabled=true");
        defaults.setProperty("live.fallback.secret", "fallback-secret");
        SpringApplication application = new SpringApplication(ConsumerConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(defaults);
        return application.run();
    }

    private static void await(BooleanSupplier condition, String failure)
            throws InterruptedException {
        long deadline = System.nanoTime() + REFRESH_TIMEOUT.toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        assertTrue(condition.getAsBoolean(), failure);
    }

    private static void refreshAndAssertRecreated(
            ConfigurableApplicationContext context,
            Lifecycle lifecycle,
            String expectedVersion,
            RemoteChange change,
            Runnable assertions)
            throws Exception {
        Consumer consumer = context.getBean(Consumer.class);
        RefreshState before = refreshState(context, lifecycle);
        change.apply();
        await(
                () ->
                        lifecycle.failures.size() > before.failures()
                                || lifecycle.completions.stream()
                                        .skip(before.completions())
                                        .anyMatch(
                                                completed ->
                                                        java.util.Objects.equals(
                                                                expectedVersion,
                                                                completed.version())),
                "未收到本轮真实推送触发的 RefreshScope 完成事件");
        assertEquals(before.failures(), lifecycle.failures.size(), "刷新出现非预期监听器异常");
        assertEquals(
                expectedVersion, context.getEnvironment().getProperty("live.snapshot-version"));
        assertEquals(before.changed() + 1, lifecycle.changedKeys.size());
        assertEquals(before.completions() + 1, lifecycle.completions.size());
        assertEquals(before.destroyed() + 2, lifecycle.destroyed.get());
        assertEquals(before.initialized(), lifecycle.initialized.get());
        assertions.run();
        assertEquals(before.initialized() + 2, lifecycle.initialized.get());
        assertNotSame(
                before.requiredTarget(),
                context.getBean("scopedTarget.requiredSettings", Settings.class));
        assertNotSame(
                before.fallbackTarget(),
                context.getBean("scopedTarget.fallbackSettings", Settings.class));
        assertSame(consumer, context.getBean(Consumer.class));
        assertSame(consumer.required, context.getBean("requiredSettings", Settings.class));
        assertSame(consumer.fallback, context.getBean("fallbackSettings", Settings.class));
    }

    private static RefreshState refreshState(
            ConfigurableApplicationContext context, Lifecycle lifecycle) {
        Settings required = context.getBean("scopedTarget.requiredSettings", Settings.class);
        Settings fallback = context.getBean("scopedTarget.fallbackSettings", Settings.class);
        List<PropertySource<?>> sources = new ArrayList<>();
        context.getEnvironment().getPropertySources().forEach(sources::add);
        return new RefreshState(
                required,
                fallback,
                sources,
                lifecycle.initialized.get(),
                lifecycle.destroyed.get(),
                lifecycle.changedKeys.size(),
                lifecycle.completions.size(),
                lifecycle.failures.size());
    }

    static void assertCurrentDecryptionFailure(
            Lifecycle lifecycle, int previousFailures, Set<String> expectedKeys) {
        List<Throwable> failures = List.copyOf(lifecycle.failures);
        assertTrue(failures.size() > previousFailures, "本轮未观察到解密失败");
        for (Throwable failure : failures.subList(previousFailures, failures.size())) {
            assertTrue(
                    isExpectedAuthenticationFailure(failure, expectedKeys),
                    "本轮异常不是预期业务键的 AES-GCM 认证失败");
        }
    }

    private static boolean isExpectedAuthenticationFailure(
            Throwable failure, Set<String> expectedKeys) {
        boolean expectedProperty = false;
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (cause instanceof IllegalStateException
                    && expectedKeys.stream()
                            .anyMatch(key -> ("无法解密 Nacos 配置项: " + key).equals(message))) {
                expectedProperty = true;
            }
            if (expectedProperty && cause instanceof AEADBadTagException) {
                return true;
            }
        }
        return false;
    }

    private static void assertFailedRefreshKeepsSourcesAndTargets(
            ConfigurableApplicationContext context, Lifecycle lifecycle, RefreshState before) {
        List<PropertySource<?>> currentSources = new ArrayList<>();
        context.getEnvironment().getPropertySources().forEach(currentSources::add);
        assertEquals(before.sources().size(), currentSources.size());
        for (int index = 0; index < before.sources().size(); index++) {
            assertSame(before.sources().get(index), currentSources.get(index));
        }
        assertEquals(before.initialized(), lifecycle.initialized.get());
        assertEquals(before.destroyed(), lifecycle.destroyed.get());
        assertEquals(before.changed(), lifecycle.changedKeys.size());
        assertEquals(before.completions(), lifecycle.completions.size());
        assertSame(
                before.requiredTarget(),
                context.getBean("scopedTarget.requiredSettings", Settings.class));
        assertSame(
                before.fallbackTarget(),
                context.getBean("scopedTarget.fallbackSettings", Settings.class));
    }

    private static void assertInitialValues(
            ConfigurableApplicationContext context, Consumer consumer) {
        assertNull(context.getEnvironment().getProperty("live.snapshot-version"));
        assertNull(context.getEnvironment().getProperty("live.required.secret"));
        assertEquals(
                "fallback-secret", context.getEnvironment().getProperty("live.fallback.secret"));
        assertNull(context.getEnvironment().getProperty(NacosEncryptProperties.PREFIX + ".key"));
        assertFalse(hasDecryptedSources(context));
        assertNull(consumer.requiredSecret());
        assertEquals("fallback-secret", consumer.fallbackSecret());
        assertEquals(30, consumer.requiredTimeout());
    }

    private static void assertSecrets(
            ConfigurableApplicationContext context, Consumer consumer, String secret) {
        assertEquals(secret, context.getEnvironment().getProperty("live.required.secret"));
        assertEquals(secret, context.getEnvironment().getProperty("live.fallback.secret"));
        assertEquals(secret, consumer.requiredSecret());
        assertEquals(secret, consumer.fallbackSecret());
    }

    private static void assertChangedKeys(Lifecycle lifecycle, String... keys) {
        assertTrue(
                lifecycle
                        .changedKeys
                        .get(lifecycle.changedKeys.size() - 1)
                        .containsAll(Set.of(keys)));
    }

    private static boolean hasDecryptedSources(ConfigurableApplicationContext context) {
        return context.getEnvironment().getPropertySources().stream()
                .anyMatch(source -> source.getName().startsWith("decryptedProperties:"));
    }

    private record RefreshState(
            Settings requiredTarget,
            Settings fallbackTarget,
            List<PropertySource<?>> sources,
            int initialized,
            int destroyed,
            int changed,
            int completions,
            int failures) {}

    private record RefreshCompletion(String version) {}

    @FunctionalInterface
    private interface RemoteChange {
        void apply() throws Exception;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class ConsumerConfiguration {

        @Bean
        Lifecycle lifecycle() {
            return new Lifecycle();
        }

        @Bean(name = AbstractApplicationContext.APPLICATION_EVENT_MULTICASTER_BEAN_NAME)
        static SimpleApplicationEventMulticaster applicationEventMulticaster(
                BeanFactory beanFactory, Lifecycle lifecycle) {
            SimpleApplicationEventMulticaster multicaster =
                    new SimpleApplicationEventMulticaster(beanFactory);
            multicaster.setErrorHandler(
                    failure -> {
                        // 同一异常可能经过内层 RefreshEvent 和外层 NacosEvent，按引用去重。
                        lifecycle.failures.addIfAbsent(failure);
                        if (failure instanceof RuntimeException runtime) {
                            throw runtime;
                        }
                        if (failure instanceof Error error) {
                            throw error;
                        }
                        throw new IllegalStateException("事件监听器传播受检异常", failure);
                    });
            return multicaster;
        }

        @Bean
        ApplicationListener<EnvironmentChangeEvent> environmentChanges(Lifecycle lifecycle) {
            return event -> lifecycle.changedKeys.add(Set.copyOf(event.getKeys()));
        }

        @Bean
        ApplicationListener<RefreshScopeRefreshedEvent> scopeRefreshes(
                Lifecycle lifecycle, ConfigurableEnvironment environment) {
            return event ->
                    lifecycle.completions.add(
                            new RefreshCompletion(
                                    environment.getProperty("live.snapshot-version")));
        }

        @Bean(initMethod = "initialize", destroyMethod = "destroy")
        @RefreshScope
        @ConfigurationProperties("live.required")
        Settings requiredSettings(Lifecycle lifecycle) {
            return new Settings(lifecycle);
        }

        @Bean(initMethod = "initialize", destroyMethod = "destroy")
        @RefreshScope
        @ConfigurationProperties("live.fallback")
        Settings fallbackSettings(Lifecycle lifecycle) {
            return new Settings(lifecycle);
        }

        @Bean
        Consumer consumer(
                @Qualifier("requiredSettings") Settings required,
                @Qualifier("fallbackSettings") Settings fallback) {
            return new Consumer(required, fallback);
        }
    }

    static class Lifecycle {
        private final AtomicInteger initialized = new AtomicInteger();
        private final AtomicInteger destroyed = new AtomicInteger();
        private final List<Set<String>> changedKeys = new CopyOnWriteArrayList<>();
        private final List<RefreshCompletion> completions = new CopyOnWriteArrayList<>();
        private final CopyOnWriteArrayList<Throwable> failures = new CopyOnWriteArrayList<>();
    }

    static class Consumer {
        private final Settings required;
        private final Settings fallback;

        Consumer(Settings required, Settings fallback) {
            this.required = required;
            this.fallback = fallback;
        }

        String requiredSecret() {
            return required.getSecret();
        }

        String fallbackSecret() {
            return fallback.getSecret();
        }

        int requiredTimeout() {
            return required.getTimeout();
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
            lifecycle.initialized.incrementAndGet();
        }

        public void destroy() {
            lifecycle.destroyed.incrementAndGet();
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
