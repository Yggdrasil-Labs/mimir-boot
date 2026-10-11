package com.yggdrasil.labs.nacos.configdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.DefaultBootstrapContext;
import org.springframework.boot.context.config.ConfigData;
import org.springframework.boot.context.config.ConfigData.Option;
import org.springframework.boot.context.config.ConfigDataResourceNotFoundException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.cloud.nacos.NacosConfigProperties;
import com.alibaba.cloud.nacos.configdata.NacosConfigDataResource;
import com.alibaba.cloud.nacos.configdata.NacosConfigDataResource.NacosItemConfig;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;

class ManagedNacosConfigDataLoaderTest {

    private final ManagedNacosConfigDataLoader loader =
            new ManagedNacosConfigDataLoader(supplier -> supplier.get());

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "# 仅有注释的语义空配置")
    void shouldMarkSuccessfulEmptyOrMissingConfigurationAndPreserveOptions(String content)
            throws Exception {
        Fixture fixture = fixture("empty.properties", true, "remote");
        when(fixture.service().getConfig(eq("empty.properties"), eq("TD039"), anyLong()))
                .thenReturn(content);

        ConfigData loaded = loader.load(() -> fixture.bootstrap(), fixture.resource());

        assertEquals(1, loaded.getPropertySources().size());
        ManagedNacosPropertySource source =
                assertInstanceOf(
                        ManagedNacosPropertySource.class, loaded.getPropertySources().get(0));
        assertEquals(0, source.getPropertyNames().length);
        assertNull(source.getProperty("app.secret"));
        assertEquals(fixture.resource().resourceKey(), source.resourceKey());
        assertTrue(loaded.getOptions(source).contains(Option.IGNORE_IMPORTS));
        assertTrue(loaded.getOptions(source).contains(Option.IGNORE_PROFILES));
        assertTrue(loaded.getOptions(source).contains(Option.PROFILE_SPECIFIC));
    }

    @Test
    void shouldPreservePropertiesAndLocalOptions() throws Exception {
        Fixture fixture = fixture("normal.properties", false, null);
        when(fixture.service().getConfig(eq("normal.properties"), eq("TD039"), anyLong()))
                .thenReturn("app.secret=plain\napp.timeout=12");

        ConfigData loaded = loader.load(() -> fixture.bootstrap(), fixture.resource());

        ManagedNacosPropertySource source =
                assertInstanceOf(
                        ManagedNacosPropertySource.class, loaded.getPropertySources().get(0));
        assertEquals("TD039@normal.properties", source.getName());
        assertEquals("plain", source.getProperty("app.secret"));
        assertEquals("12", source.getProperty("app.timeout"));
        assertTrue(loaded.getOptions(source).contains(Option.IGNORE_IMPORTS));
        assertTrue(loaded.getOptions(source).contains(Option.IGNORE_PROFILES));
        assertFalse(loaded.getOptions(source).contains(Option.PROFILE_SPECIFIC));
    }

    @Test
    void shouldPreserveYamlDocumentNamesOrderAndOptions() throws Exception {
        Fixture fixture = fixture("documents.yaml", false, "remote");
        when(fixture.service().getConfig(eq("documents.yaml"), eq("TD039"), anyLong()))
                .thenReturn("app:\n  timeout: 12\n---\napp:\n  timeout: 30\n");

        ConfigData loaded = loader.load(() -> fixture.bootstrap(), fixture.resource());

        assertEquals(
                List.of("TD039@documents.yaml (document #0)", "TD039@documents.yaml (document #1)"),
                loaded.getPropertySources().stream().map(source -> source.getName()).toList());
        assertEquals(12, loaded.getPropertySources().get(0).getProperty("app.timeout"));
        assertEquals(30, loaded.getPropertySources().get(1).getProperty("app.timeout"));
        for (var source : loaded.getPropertySources()) {
            ManagedNacosPropertySource managed =
                    assertInstanceOf(ManagedNacosPropertySource.class, source);
            assertEquals(fixture.resource().resourceKey(), managed.resourceKey());
            assertTrue(loaded.getOptions(managed).contains(Option.PROFILE_SPECIFIC));
            // Nacos parser 已剥离 origin 值，适配器不能伪造位置信息。
            assertNull(managed.getOrigin("app.timeout"));
        }
    }

    @Test
    void shouldNotTurnOptionalRequestFailureIntoSuccessfulEmptyMarker() throws Exception {
        Fixture fixture = fixture("optional.properties", true, null);
        when(fixture.service().getConfig(eq("optional.properties"), eq("TD039"), anyLong()))
                .thenThrow(new NacosException(NacosException.SERVER_ERROR, "测试请求失败"));

        assertNull(loader.load(() -> fixture.bootstrap(), fixture.resource()));
    }

    @Test
    void shouldPropagateRequiredRequestFailureWithOriginalCause() throws Exception {
        Fixture fixture = fixture("required.properties", false, null);
        NacosException failure = new NacosException(NacosException.SERVER_ERROR, "测试请求失败");
        when(fixture.service().getConfig(eq("required.properties"), eq("TD039"), anyLong()))
                .thenThrow(failure);

        ConfigDataResourceNotFoundException thrown =
                assertThrows(
                        ConfigDataResourceNotFoundException.class,
                        () -> loader.load(() -> fixture.bootstrap(), fixture.resource()));

        assertSame(failure, thrown.getCause());
        assertSame(fixture.resource().delegate(), thrown.getResource());
    }

    private Fixture fixture(String dataId, boolean optional, String preference) {
        ConfigService service = mock(ConfigService.class);
        NacosConfigManager manager = mock(NacosConfigManager.class);
        when(manager.getConfigService()).thenReturn(service);
        NacosConfigProperties properties = new NacosConfigProperties();
        properties.setServerAddr("127.0.0.1:8848");
        properties.setNamespace("test-namespace");
        DefaultBootstrapContext bootstrap = new DefaultBootstrapContext();
        bootstrap.register(NacosConfigManager.class, context -> manager);
        bootstrap.register(NacosConfigProperties.class, context -> properties);
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test-input", Map.of()));
        bootstrap.register(Binder.class, context -> Binder.get(environment));
        NacosItemConfig config =
                new NacosItemConfig()
                        .setGroup("TD039")
                        .setDataId(dataId)
                        .setSuffix(dataId.substring(dataId.lastIndexOf('.') + 1))
                        .setRefreshEnabled(true)
                        .setPreference(preference);
        ManagedNacosConfigDataResource resource =
                new ManagedNacosConfigDataResource(
                        new NacosConfigDataResource(
                                properties, optional, null, LogFactory.getLog(getClass()), config));
        return new Fixture(bootstrap, service, resource);
    }

    private record Fixture(
            DefaultBootstrapContext bootstrap,
            ConfigService service,
            ManagedNacosConfigDataResource resource) {}
}
