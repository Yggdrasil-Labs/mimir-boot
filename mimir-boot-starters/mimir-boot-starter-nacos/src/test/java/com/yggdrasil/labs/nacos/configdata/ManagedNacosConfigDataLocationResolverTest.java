package com.yggdrasil.labs.nacos.configdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultBootstrapContext;
import org.springframework.boot.context.config.ConfigDataLocation;
import org.springframework.boot.context.config.ConfigDataLocationResolverContext;
import org.springframework.boot.context.config.ConfigDataResource;
import org.springframework.boot.context.config.Profiles;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.cloud.nacos.NacosConfigProperties;

class ManagedNacosConfigDataLocationResolverTest {

    private final ManagedNacosConfigDataLocationResolver resolver =
            new ManagedNacosConfigDataLocationResolver(supplier -> supplier.get());

    @Test
    void shouldDelegateProfileResolutionAndPreserveImportParameters() {
        ResolverContext context = context(Map.of());
        ConfigDataLocation location =
                ConfigDataLocation.of(
                        "optional:nacos:settings.yaml?group=OTHER&refreshEnabled=false&preference=remote");
        Profiles profiles = mock(Profiles.class);
        when(profiles.getAccepted()).thenReturn(List.of("contract"));

        assertTrue(resolver.isResolvable(context, location));
        assertTrue(resolver.resolve(context, location).isEmpty());
        List<ManagedNacosConfigDataResource> resources =
                resolver.resolveProfileSpecific(context, location, profiles);

        assertEquals(1, resources.size());
        var original = resources.get(0).delegate();
        assertTrue(original.isOptional());
        assertEquals("settings.yaml", original.getConfig().getDataId());
        assertEquals("OTHER", original.getConfig().getGroup());
        assertEquals("yaml", original.getConfig().getSuffix());
        assertFalse(original.getConfig().isRefreshEnabled());
        assertEquals("remote", original.getConfig().getPreference());
        assertEquals("contract", original.getProfiles());
        assertEquals(-2, resolver.getOrder());
        assertSame(
                original.getProperties(),
                context.getBootstrapContext().get(NacosConfigProperties.class));
        assertSame(context.manager, context.getBootstrapContext().get(NacosConfigManager.class));
    }

    @Test
    void shouldDelegateConfiguredExtensionWhenDataIdHasNoSuffix() {
        ResolverContext context =
                context(Map.of("spring.cloud.nacos.config.file-extension", "yaml"));
        Profiles profiles = mock(Profiles.class);

        ManagedNacosConfigDataResource resource =
                resolver.resolveProfileSpecific(
                                context, ConfigDataLocation.of("nacos:settings"), profiles)
                        .get(0);

        assertEquals("yaml", resource.delegate().getConfig().getSuffix());
        assertEquals("DEFAULT_GROUP", resource.delegate().getConfig().getGroup());
        assertTrue(resource.delegate().getConfig().isRefreshEnabled());
    }

    @Test
    void shouldLeaveNonNacosAndDisabledImportsUnclaimed() {
        assertFalse(
                resolver.isResolvable(
                        context(Map.of()), ConfigDataLocation.of("file:settings.yaml")));
        assertFalse(
                resolver.isResolvable(
                        context(Map.of("spring.cloud.nacos.config.enabled", "false")),
                        ConfigDataLocation.of("optional:nacos:settings.yaml")));
    }

    @Test
    void shouldKeepResourceOwnershipStableAcrossNewPropertiesAndCredentialChanges() {
        Profiles profiles = mock(Profiles.class);
        ConfigDataLocation location = ConfigDataLocation.of("nacos:settings.properties");
        ManagedNacosConfigDataResource first =
                resolver.resolveProfileSpecific(
                                context(
                                        Map.of(
                                                "spring.cloud.nacos.config.password",
                                                "first-test-password")),
                                location,
                                profiles)
                        .get(0);
        ManagedNacosConfigDataResource next =
                resolver.resolveProfileSpecific(
                                context(
                                        Map.of(
                                                "spring.cloud.nacos.config.password",
                                                "second-test-password")),
                                location,
                                profiles)
                        .get(0);

        assertNotEquals(first.delegate().getProperties(), next.delegate().getProperties());
        assertEquals(first.resourceKey(), next.resourceKey());
        assertEquals(
                new NacosConfigDataResourceKey(
                        "127.0.0.1:8848", "test-namespace", "DEFAULT_GROUP", "settings.properties"),
                next.resourceKey());
        assertFalse(next.toString().contains("second-test-password"));
    }

    @Test
    void shouldNormalizeMissingOwnershipFieldsWithoutMergingDifferentNamespaces() {
        assertEquals(
                new NacosConfigDataResourceKey("", "", "", ""),
                new NacosConfigDataResourceKey(null, null, null, null));
        assertNotEquals(
                new NacosConfigDataResourceKey("server", "namespace-a", "group", "data"),
                new NacosConfigDataResourceKey("server", "namespace-b", "group", "data"));
    }

    private ResolverContext context(Map<String, Object> overrides) {
        StandardEnvironment environment = new StandardEnvironment();
        environment
                .getPropertySources()
                .addFirst(
                        new MapPropertySource(
                                "base-input",
                                Map.of(
                                        "spring.cloud.nacos.config.server-addr",
                                        "127.0.0.1:8848",
                                        "spring.cloud.nacos.config.namespace",
                                        "test-namespace")));
        environment.getPropertySources().addFirst(new MapPropertySource("overrides", overrides));
        NacosConfigManager manager = mock(NacosConfigManager.class);
        DefaultBootstrapContext bootstrap = new DefaultBootstrapContext();
        bootstrap.register(NacosConfigManager.class, ignored -> manager);
        return new ResolverContext(Binder.get(environment), bootstrap, manager);
    }

    private record ResolverContext(
            Binder binder, DefaultBootstrapContext bootstrap, NacosConfigManager manager)
            implements ConfigDataLocationResolverContext {

        @Override
        public Binder getBinder() {
            return binder;
        }

        @Override
        public ConfigDataResource getParent() {
            return null;
        }

        @Override
        public DefaultBootstrapContext getBootstrapContext() {
            return bootstrap;
        }
    }
}
