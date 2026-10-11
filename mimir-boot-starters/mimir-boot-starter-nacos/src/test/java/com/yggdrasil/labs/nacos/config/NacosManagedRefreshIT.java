package com.yggdrasil.labs.nacos.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ConfigurableBootstrapContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.autoconfigure.ConfigurationPropertiesRebinderAutoConfiguration;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.cloud.nacos.configdata.NacosConfigRefreshEventListener;
import com.alibaba.cloud.nacos.refresh.NacosConfigRefreshEvent;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.yggdrasil.labs.nacos.configdata.ManagedNacosPropertySource;
import com.yggdrasil.labs.nacos.crypto.ConfigCryptoUtils;

/** 真实 ConfigData SPI 和完整刷新流程，仅替换远程 ConfigService 边界。 */
class NacosManagedRefreshIT {

    @Test
    void shouldReconcileRealConfigDataImportsBeforeEventsAndScopedConsumerRecreation()
            throws Exception {
        String run = UUID.randomUUID().toString();
        String group = "MIMIR_MANAGED_" + run;
        String propertiesId = run + ".properties";
        String optionalId = run + "-optional.properties";
        String yamlId = run + ".yaml";
        String key = ConfigCryptoUtils.generateKey();
        AtomicReference<RemoteState> remote =
                new AtomicReference<>(
                        new RemoteState(
                                Map.of(
                                        propertiesId, encryptedSnapshot("1", "old-secret", key),
                                        optionalId, "optional.value=previous\n",
                                        yamlId, "yaml.first: first\n---\nyaml.removed: second\n"),
                                Set.of()));
        ConfigService service = mock(ConfigService.class);
        when(service.getConfig(anyString(), anyString(), anyLong()))
                .thenAnswer(
                        invocation -> {
                            String dataId = invocation.getArgument(0);
                            if (remote.get().failures().contains(dataId)) {
                                throw new NacosException(500, "测试远程请求失败");
                            }
                            return remote.get().contents().get(dataId);
                        });
        NacosConfigManager manager = mock(NacosConfigManager.class);
        when(manager.getConfigService()).thenReturn(service);

        try (ConfigurableApplicationContext context =
                start(manager, propertiesId, optionalId, yamlId, group)) {
            assertSame(
                    manager,
                    context.getBean(ConfigurableBootstrapContext.class)
                            .get(NacosConfigManager.class));
            assertEquals(
                    NacosConfigDataContextRefresher.class,
                    context.getBean(ContextRefresher.class).getClass());
            Consumer consumer = context.getBean(Consumer.class);
            Settings requiredProxy = consumer.required;
            Settings fallbackProxy = consumer.fallback;
            Lifecycle lifecycle = context.getBean(Lifecycle.class);
            assertEquals("old-secret", consumer.requiredSecret());
            assertEquals("old-secret", consumer.fallbackSecret());
            assertEquals(12, consumer.requiredTimeout());
            assertTrue(managedSources(context, propertiesId).size() > 0);
            assertEquals(2, managedSources(context, yamlId).size());

            // 保留源与加密前缀，单独删除业务键必须进入环境差异。
            replace(
                    remote,
                    propertiesId,
                    propertiesSnapshot("2", Map.of(NacosEncryptProperties.PREFIX + ".key", key)));
            refreshAndAssertRecreated(
                    context,
                    lifecycle,
                    () -> {
                        assertNull(consumer.requiredSecret());
                        assertEquals("fallback-secret", consumer.fallbackSecret());
                        assertEquals(30, consumer.requiredTimeout());
                    });
            assertDeletedKeys(lifecycle, "managed.required.secret", "managed.required.timeout");

            // 删除加密前缀且保留普通业务值，旧解密覆盖层不能遮盖新源。
            replace(
                    remote,
                    propertiesId,
                    propertiesSnapshot("3", Map.of("managed.required.secret", "plain")));
            refreshAndAssertRecreated(
                    context,
                    lifecycle,
                    () -> {
                        assertEquals("plain", consumer.requiredSecret());
                        assertEquals("fallback-secret", consumer.fallbackSecret());
                    });
            assertNull(
                    context.getEnvironment().getProperty(NacosEncryptProperties.PREFIX + ".key"));
            assertFalse(hasDecryptedSources(context));
            assertDeletedKeys(lifecycle, NacosEncryptProperties.PREFIX + ".key");

            replace(remote, propertiesId, encryptedSnapshot("4", "restored-secret", key));
            refreshAndAssertRecreated(
                    context,
                    lifecycle,
                    () -> {
                        assertEquals("restored-secret", consumer.requiredSecret());
                        assertEquals("restored-secret", consumer.fallbackSecret());
                    });

            // 语义空和 DataId 删除都必须由真实 loader 产生成功空标记。
            replace(remote, propertiesId, "# 配置语义为空\n");
            refreshAndAssertRecreated(context, lifecycle, () -> assertInitialValues(consumer));
            assertNull(context.getEnvironment().getProperty("managed.snapshot-version"));
            assertFalse(hasDecryptedSources(context));
            assertEquals(1, managedSources(context, propertiesId).size());
            assertEquals(0, managedSources(context, propertiesId).get(0).getPropertyNames().length);
            assertDeletedKeys(lifecycle, "managed.required.secret", "managed.snapshot-version");

            replace(remote, propertiesId, encryptedSnapshot("5", "before-delete", key));
            refreshAndAssertRecreated(
                    context,
                    lifecycle,
                    () -> {
                        assertEquals("before-delete", consumer.requiredSecret());
                        assertEquals("before-delete", consumer.fallbackSecret());
                    });
            replace(remote, propertiesId, null);
            refreshAndAssertRecreated(context, lifecycle, () -> assertInitialValues(consumer));
            assertNull(context.getEnvironment().getProperty("managed.required.secret"));
            assertEquals(
                    "fallback-secret",
                    context.getEnvironment().getProperty("managed.fallback.secret"));
            assertFalse(hasDecryptedSources(context));
            assertDeletedKeys(lifecycle, "managed.required.secret", "managed.required.timeout");

            replace(remote, propertiesId, encryptedSnapshot("6", "stable-secret", key));
            refreshAndAssertRecreated(
                    context,
                    lifecycle,
                    () -> {
                        assertEquals("stable-secret", consumer.requiredSecret());
                        assertEquals("stable-secret", consumer.fallbackSecret());
                    });

            // 显式保留的旧文档不是本轮加载成功标记，失败或移除 import 不能误删其他文档。
            List<ManagedNacosPropertySource> previousYaml = managedSources(context, yamlId);
            failRequests(remote, Set.of(yamlId));
            refreshAndAssertRecreated(
                    context,
                    lifecycle,
                    () -> {
                        assertEquals("stable-secret", consumer.requiredSecret());
                        assertEquals("stable-secret", consumer.fallbackSecret());
                    });
            assertEquals("second", context.getEnvironment().getProperty("yaml.removed"));
            assertSame(previousYaml.get(0), managedSources(context, yamlId).get(0));
            assertSame(previousYaml.get(1), managedSources(context, yamlId).get(1));
            failRequests(remote, Set.of());
            MapPropertySource defaults =
                    (MapPropertySource)
                            context.getEnvironment().getPropertySources().get("defaultProperties");
            Object allImports = defaults.getProperty("spring.config.import");
            defaults.getSource()
                    .put(
                            "spring.config.import",
                            String.join(
                                    ",",
                                    importLocation(propertiesId, group),
                                    "optional:" + importLocation(optionalId, group)));
            refreshAndAssertRecreated(
                    context,
                    lifecycle,
                    () -> {
                        assertEquals("stable-secret", consumer.requiredSecret());
                        assertEquals("stable-secret", consumer.fallbackSecret());
                    });
            assertEquals("second", context.getEnvironment().getProperty("yaml.removed"));
            assertSame(previousYaml.get(0), managedSources(context, yamlId).get(0));
            assertSame(previousYaml.get(1), managedSources(context, yamlId).get(1));
            defaults.getSource().put("spring.config.import", allImports);

            replace(remote, yamlId, "yaml.first: first\n");
            refreshAndAssertRecreated(
                    context,
                    lifecycle,
                    () -> {
                        assertEquals("stable-secret", consumer.requiredSecret());
                        assertEquals("stable-secret", consumer.fallbackSecret());
                    });
            assertEquals("first", context.getEnvironment().getProperty("yaml.first"));
            assertNull(context.getEnvironment().getProperty("yaml.removed"));
            assertEquals(1, managedSources(context, yamlId).size());
            assertDeletedKeys(lifecycle, "yaml.removed");

            // optional 被上游吞掉的请求失败保留旧源，但仍完成作用域失效。
            PropertySource<?> previousOptional = managedSources(context, optionalId).get(0);
            failRequests(remote, Set.of(optionalId));
            refreshAndAssertRecreated(
                    context,
                    lifecycle,
                    () -> {
                        assertEquals("stable-secret", consumer.requiredSecret());
                        assertEquals("stable-secret", consumer.fallbackSecret());
                    });
            assertEquals("previous", context.getEnvironment().getProperty("optional.value"));
            assertSame(previousOptional, managedSources(context, optionalId).get(0));

            // 传播出的加载或解密错误不得发布成功事件，也不得销毁现有目标。
            failRequests(remote, Set.of(propertiesId));
            assertFailedRefreshKeepsSourcesAndTargets(context, lifecycle, consumer);
            failRequests(remote, Set.of());
            String invalidKey = ConfigCryptoUtils.generateKey();
            replace(
                    remote,
                    propertiesId,
                    encryptedSnapshot("7", "invalid-secret", key, invalidKey));
            assertFailedRefreshKeepsSourcesAndTargets(context, lifecycle, consumer);

            replace(remote, propertiesId, encryptedSnapshot("8", "recovered-secret", key));
            refreshAndAssertRecreated(
                    context,
                    lifecycle,
                    () -> {
                        assertEquals("recovered-secret", consumer.requiredSecret());
                        assertEquals("recovered-secret", consumer.fallbackSecret());
                    });
            refreshAndAssertRecreated(
                    context,
                    lifecycle,
                    () -> {
                        assertEquals("recovered-secret", consumer.requiredSecret());
                        assertEquals("recovered-secret", consumer.fallbackSecret());
                    });
            assertSame(requiredProxy, consumer.required);
            assertSame(fallbackProxy, consumer.fallback);
            assertSame(consumer, context.getBean(Consumer.class));
        }
    }

    private static ConfigurableApplicationContext start(
            NacosConfigManager manager,
            String propertiesId,
            String optionalId,
            String yamlId,
            String group) {
        Properties defaults = new Properties();
        defaults.setProperty("spring.application.name", "mimir-managed-offline-consumer");
        defaults.setProperty("spring.cloud.nacos.config.server-addr", "127.0.0.1:1");
        defaults.setProperty(
                "spring.config.location", "optional:classpath:/managed-test.properties");
        defaults.setProperty(
                "spring.config.import",
                String.join(
                        ",",
                        importLocation(propertiesId, group),
                        "optional:" + importLocation(optionalId, group),
                        "optional:" + importLocation(yamlId, group)));
        defaults.setProperty(
                "spring.cloud.refresh.additional-property-sources-to-retain[0]",
                group + "@" + yamlId + " (document #0)");
        defaults.setProperty("managed.fallback.secret", "fallback-secret");
        SpringApplication application = new SpringApplication(ConsumerConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(defaults);
        application.addBootstrapRegistryInitializer(
                registry -> {
                    registry.register(NacosConfigManager.class, bootstrap -> manager);
                });
        return application.run();
    }

    private static String importLocation(String dataId, String group) {
        // 离线夹具不向静态 repository 留下可被后续真实服务应用注册的监听项。
        return "nacos:" + dataId + "?group=" + group + "&refreshEnabled=false";
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
                        "managed.required.secret",
                        encrypted,
                        "managed.required.timeout",
                        "12",
                        "managed.fallback.secret",
                        encrypted,
                        "managed.fallback.timeout",
                        "12"));
    }

    private static String propertiesSnapshot(String version, Map<String, String> values)
            throws Exception {
        Properties properties = new Properties();
        properties.putAll(values);
        properties.setProperty("managed.snapshot-version", version);
        StringWriter writer = new StringWriter();
        properties.store(writer, null);
        return writer.toString();
    }

    private static void replace(AtomicReference<RemoteState> state, String dataId, String content) {
        Map<String, String> contents = new HashMap<>(state.get().contents());
        if (content == null) {
            contents.remove(dataId);
        } else {
            contents.put(dataId, content);
        }
        state.set(new RemoteState(Map.copyOf(contents), state.get().failures()));
    }

    private static void failRequests(AtomicReference<RemoteState> state, Set<String> failures) {
        state.set(new RemoteState(state.get().contents(), failures));
    }

    private static void refresh(ConfigurableApplicationContext context) {
        context.publishEvent(new NacosConfigRefreshEvent(context, null, "离线远程边界刷新"));
    }

    private static void refreshAndAssertRecreated(
            ConfigurableApplicationContext context, Lifecycle lifecycle, Runnable assertions) {
        Settings previousRequired =
                context.getBean("scopedTarget.requiredSettings", Settings.class);
        Settings previousFallback =
                context.getBean("scopedTarget.fallbackSettings", Settings.class);
        int beforeDestroyed = lifecycle.destroyed.get();
        int beforeInitialized = lifecycle.initialized.get();
        int beforeChanged = lifecycle.changedKeys.size();
        int beforeRefreshes = lifecycle.refreshes.get();
        refresh(context);
        assertEquals(beforeChanged + 1, lifecycle.changedKeys.size());
        assertEquals(beforeRefreshes + 1, lifecycle.refreshes.get());
        assertEquals(beforeDestroyed + 2, lifecycle.destroyed.get());
        assertEquals(beforeInitialized, lifecycle.initialized.get());
        assertions.run();
        assertEquals(beforeInitialized + 2, lifecycle.initialized.get());
        assertNotSame(
                previousRequired, context.getBean("scopedTarget.requiredSettings", Settings.class));
        assertNotSame(
                previousFallback, context.getBean("scopedTarget.fallbackSettings", Settings.class));
    }

    private static void assertFailedRefreshKeepsSourcesAndTargets(
            ConfigurableApplicationContext context, Lifecycle lifecycle, Consumer consumer) {
        List<PropertySource<?>> previousSources = new ArrayList<>();
        context.getEnvironment().getPropertySources().forEach(previousSources::add);
        Settings previousRequired =
                context.getBean("scopedTarget.requiredSettings", Settings.class);
        Settings previousFallback =
                context.getBean("scopedTarget.fallbackSettings", Settings.class);
        int beforeChanged = lifecycle.changedKeys.size();
        int beforeRefreshes = lifecycle.refreshes.get();
        int beforeDestroyed = lifecycle.destroyed.get();
        assertThrows(RuntimeException.class, () -> refresh(context));
        List<PropertySource<?>> currentSources = new ArrayList<>();
        context.getEnvironment().getPropertySources().forEach(currentSources::add);
        assertEquals(previousSources.size(), currentSources.size());
        for (int index = 0; index < previousSources.size(); index++) {
            assertSame(previousSources.get(index), currentSources.get(index));
        }
        assertEquals(beforeChanged, lifecycle.changedKeys.size());
        assertEquals(beforeRefreshes, lifecycle.refreshes.get());
        assertEquals(beforeDestroyed, lifecycle.destroyed.get());
        assertEquals(
                "stable-secret", context.getEnvironment().getProperty("managed.required.secret"));
        assertEquals("stable-secret", consumer.requiredSecret());
        assertEquals("stable-secret", consumer.fallbackSecret());
        assertSame(
                previousRequired, context.getBean("scopedTarget.requiredSettings", Settings.class));
        assertSame(
                previousFallback, context.getBean("scopedTarget.fallbackSettings", Settings.class));
    }

    private static List<ManagedNacosPropertySource> managedSources(
            ConfigurableApplicationContext context, String dataId) {
        return context.getEnvironment().getPropertySources().stream()
                .filter(ManagedNacosPropertySource.class::isInstance)
                .map(ManagedNacosPropertySource.class::cast)
                .filter(source -> dataId.equals(source.resourceKey().dataId()))
                .toList();
    }

    private static boolean hasDecryptedSources(ConfigurableApplicationContext context) {
        return context.getEnvironment().getPropertySources().stream()
                .anyMatch(source -> source.getName().startsWith("decryptedProperties:"));
    }

    private static void assertDeletedKeys(Lifecycle lifecycle, String... keys) {
        assertTrue(
                lifecycle
                        .changedKeys
                        .get(lifecycle.changedKeys.size() - 1)
                        .containsAll(Set.of(keys)));
    }

    private static void assertInitialValues(Consumer consumer) {
        assertNull(consumer.requiredSecret());
        assertEquals("fallback-secret", consumer.fallbackSecret());
        assertEquals(30, consumer.requiredTimeout());
    }

    private record RemoteState(Map<String, String> contents, Set<String> failures) {}

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({
        NacosEncryptAutoConfiguration.class,
        NacosRefreshAutoConfiguration.class,
        RefreshAutoConfiguration.class,
        ConfigurationPropertiesRebinderAutoConfiguration.class
    })
    static class ConsumerConfiguration {
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

        @Bean
        ApplicationListener<RefreshScopeRefreshedEvent> refreshes(Lifecycle lifecycle) {
            return event -> lifecycle.refreshes.incrementAndGet();
        }

        @Bean(initMethod = "initialize", destroyMethod = "destroy")
        @RefreshScope
        @ConfigurationProperties("managed.required")
        Settings requiredSettings(Lifecycle lifecycle) {
            return new Settings(lifecycle);
        }

        @Bean(initMethod = "initialize", destroyMethod = "destroy")
        @RefreshScope
        @ConfigurationProperties("managed.fallback")
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
        private final AtomicInteger refreshes = new AtomicInteger();
        private final List<Set<String>> changedKeys = new ArrayList<>();
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
