package com.yggdrasil.labs.nacos.configdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultBootstrapContext;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.ConfigService;

class ManagedNacosConfigDataTest {

    @Test
    void shouldRetainPropertiesValuesAndNamesWithExplicitOwnership() throws Exception {
        StandardEnvironment environment =
                load("app.secret=plain\napp.timeout=12", "properties", "");
        List<ManagedNacosPropertySource> sources = ownedSources(environment);

        assertEquals(1, sources.size(), "正常配置应通过已注册的归属 loader");
        assertEquals("TD039@td039-test.properties", sources.get(0).getName());
        assertEquals("plain", environment.getProperty("app.secret"));
        assertEquals("12", environment.getProperty("app.timeout"));
        assertEquals(
                new NacosConfigDataResourceKey(
                        "127.0.0.1:8848", "", "TD039", "td039-test.properties"),
                sources.get(0).resourceKey());
    }

    @Test
    void shouldRetainYamlDocumentPrecedenceThroughRealBootRouting() throws Exception {
        StandardEnvironment environment =
                load(
                        "app:\n  timeout: 12\n---\napp:\n  timeout: 30\n",
                        "yaml",
                        "&preference=remote");

        assertEquals("30", environment.getProperty("app.timeout"));
        assertEquals(
                List.of(
                        "TD039@td039-test.yaml (document #1)",
                        "TD039@td039-test.yaml (document #0)"),
                ownedSources(environment).stream().map(source -> source.getName()).toList());
    }

    @Test
    void shouldMarkSuccessfulEmptyLoadThroughRealConfigDataRouting() throws Exception {
        StandardEnvironment environment = load("", "properties", "");

        assertTrue(
                environment.getPropertySources().stream()
                        .anyMatch(
                                source ->
                                        source instanceof ManagedNacosPropertySource managed
                                                && managed.getPropertyNames().length == 0),
                "成功空 Nacos 结果应保留可辨认的零键归属标记");
    }

    private StandardEnvironment load(String content, String extension, String query)
            throws Exception {
        ConfigService service = mock(ConfigService.class);
        when(service.getConfig(eq("td039-test." + extension), eq("TD039"), anyLong()))
                .thenReturn(content);
        NacosConfigManager manager = mock(NacosConfigManager.class);
        when(manager.getConfigService()).thenReturn(service);
        DefaultBootstrapContext bootstrap = new DefaultBootstrapContext();
        bootstrap.register(NacosConfigManager.class, context -> manager);
        StandardEnvironment environment = new StandardEnvironment();
        environment
                .getPropertySources()
                .addFirst(
                        new MapPropertySource(
                                "test-input",
                                Map.of(
                                        "spring.config.import",
                                        "nacos:td039-test." + extension + "?group=TD039" + query,
                                        "spring.cloud.nacos.config.server-addr",
                                        "127.0.0.1:8848")));
        ConfigDataEnvironmentPostProcessor.applyTo(environment, null, bootstrap, List.of());
        return environment;
    }

    private List<ManagedNacosPropertySource> ownedSources(StandardEnvironment environment) {
        return environment.getPropertySources().stream()
                .filter(ManagedNacosPropertySource.class::isInstance)
                .map(ManagedNacosPropertySource.class::cast)
                .toList();
    }
}
