package com.yggdrasil.labs.common.page;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ColaOptionalDependencyTest {

    @Test
    void common_models_load_and_serialize_without_cola_on_classpath() throws Exception {
        List<URL> urls = new ArrayList<>();
        int removed = 0;
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            if (entry.contains("cola-component-dto")) {
                removed++;
                continue;
            }
            urls.add(Path.of(entry).toUri().toURL());
        }
        assertEquals(1, removed, "测试类路径应恰好包含一个 COLA 制品，便于隔离");

        // 父加载器为平台类加载器，应用类路径上的 COLA 无法经委派可见
        try (URLClassLoader isolated =
                new URLClassLoader(
                        urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader())) {
            Class<?> probe = Class.forName(ColaFreeProbe.class.getName(), true, isolated);
            assertSame(isolated, probe.getClassLoader());

            String json = (String) probe.getMethod("run").invoke(null);

            assertTrue(json.contains("\"status\":\"PARTIAL_SUCCESS\""), json);
            assertTrue(json.contains("\"data\":[\"1\",\"2\"]"), json);
            assertTrue(json.contains("\"code\":\"00001\""), json);

            assertThrows(
                    ClassNotFoundException.class,
                    () -> Class.forName("com.alibaba.cola.dto.PageResponse", false, isolated));
        }
    }

    @Test
    void converter_is_usable_when_cola_is_present() {
        assertTrue(ColaPageConverters.toCola(PageResult.of(List.of(1), 1L, 1L, 1L)).isSuccess());
    }
}
