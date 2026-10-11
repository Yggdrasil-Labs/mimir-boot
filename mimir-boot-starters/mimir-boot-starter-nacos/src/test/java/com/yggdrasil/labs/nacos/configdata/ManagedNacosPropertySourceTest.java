package com.yggdrasil.labs.nacos.configdata;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.boot.origin.OriginTrackedValue;
import org.springframework.boot.origin.TextResourceOrigin;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;

class ManagedNacosPropertySourceTest {

    @Test
    void shouldKeepOrdinaryMapWithoutInventingOriginOrImmutableMetadata() {
        MapPropertySource original = new MapPropertySource("plain", Map.of("app.value", "plain"));
        ManagedNacosPropertySource managed =
                new ManagedNacosPropertySource(
                        new NacosConfigDataResourceKey("server", null, "group", "data"), original);

        assertEquals("plain", managed.getProperty("app.value"));
        assertNull(managed.getProperty("app.absent"));
        assertNull(managed.getOrigin("app.value"));
        assertNull(managed.getPrefix());
        assertFalse(managed.isImmutable());
    }

    @Test
    void shouldPreserveExistingOriginLookupAndImmutableValues() {
        TextResourceOrigin origin =
                new TextResourceOrigin(
                        new ClassPathResource("application.properties"),
                        new TextResourceOrigin.Location(1, 2));
        OriginTrackedMapPropertySource original =
                new OriginTrackedMapPropertySource(
                        "original",
                        Map.of("app.value", OriginTrackedValue.of("value", origin)),
                        true);
        ManagedNacosPropertySource managed =
                new ManagedNacosPropertySource(
                        new NacosConfigDataResourceKey("server", null, "group", "data"), original);

        assertSame(original, managed.getSource());
        assertArrayEquals(original.getPropertyNames(), managed.getPropertyNames());
        assertEquals("value", managed.getProperty("app.value"));
        assertSame(origin, managed.getOrigin("app.value"));
        assertTrue(managed.isImmutable(), "归属包装应保留上游 OriginLookup 的不可变标记");
    }
}
