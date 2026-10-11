package com.yggdrasil.labs.nacos.config;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import com.yggdrasil.labs.nacos.configdata.ManagedNacosPropertySource;
import com.yggdrasil.labs.nacos.configdata.NacosConfigDataResourceKey;

class NacosConfigDataContextRefresherTest {

    private final NacosConfigDataResourceKey key =
            new NacosConfigDataResourceKey("localhost:8848", "test", "group", "app.yaml");

    @Test
    void shouldRemoveDisappearedDocumentsOnlyForSuccessfullyReloadedResource() {
        StandardEnvironment live = new StandardEnvironment();
        StandardEnvironment reloaded = new StandardEnvironment();
        ManagedNacosPropertySource oldDocument = owned(key, "document-1");
        ManagedNacosPropertySource nextDocument = owned(key, "document-0");
        ManagedNacosPropertySource unrelated =
                owned(
                        new NacosConfigDataResourceKey(
                                "localhost:8848", "another-namespace", "group", "app.yaml"),
                        "another-resource");
        live.getPropertySources().addFirst(oldDocument);
        live.getPropertySources().addFirst(nextDocument);
        live.getPropertySources().addLast(unrelated);
        reloaded.getPropertySources().addFirst(nextDocument);

        NacosConfigDataContextRefresher.reconcileNacosSources(
                live, reloaded, Map.of("document-1", oldDocument, "another-resource", unrelated));

        assertNull(live.getPropertySources().get("document-1"));
        assertSame(nextDocument, live.getPropertySources().get("document-0"));
        assertSame(unrelated, live.getPropertySources().get("another-resource"));
    }

    @Test
    void shouldRetainExistingSourceWhenReloadHasNoSuccessfulMarker() {
        StandardEnvironment live = new StandardEnvironment();
        ManagedNacosPropertySource previous = owned(key, "previous");
        live.getPropertySources().addFirst(previous);

        NacosConfigDataContextRefresher.reconcileNacosSources(
                live, new StandardEnvironment(), Map.of("previous", previous));

        assertSame(previous, live.getPropertySources().get("previous"));
    }

    @Test
    void shouldNotTreatRetainedOldDocumentAsSuccessfulReload() {
        StandardEnvironment live = new StandardEnvironment();
        StandardEnvironment reloaded = new StandardEnvironment();
        ManagedNacosPropertySource retained = owned(key, "document-0");
        ManagedNacosPropertySource other = owned(key, "document-1");
        live.getPropertySources().addFirst(retained);
        live.getPropertySources().addLast(other);
        reloaded.getPropertySources().addFirst(retained);

        NacosConfigDataContextRefresher.reconcileNacosSources(
                live, reloaded, Map.of("document-0", retained, "document-1", other));

        assertSame(other, live.getPropertySources().get("document-1"));
    }

    @Test
    void shouldPreserveApplicationSourceWithNameOfOldDocument() {
        StandardEnvironment live = new StandardEnvironment();
        StandardEnvironment reloaded = new StandardEnvironment();
        MapPropertySource application =
                new MapPropertySource("document-1", Map.of("app.custom", "retained"));
        ManagedNacosPropertySource empty = owned(key, "empty-resource");
        live.getPropertySources().addFirst(application);
        live.getPropertySources().addFirst(empty);
        reloaded.getPropertySources().addFirst(empty);

        NacosConfigDataContextRefresher.reconcileNacosSources(
                live, reloaded, Map.of("document-1", application));

        assertSame(application, live.getPropertySources().get("document-1"));
        assertSame(empty, live.getPropertySources().get("empty-resource"));
    }

    private ManagedNacosPropertySource owned(NacosConfigDataResourceKey resource, String name) {
        return new ManagedNacosPropertySource(resource, new MapPropertySource(name, Map.of()));
    }
}
