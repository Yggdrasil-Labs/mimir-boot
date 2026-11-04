package com.yggdrasil.labs.mybatis.page;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class PaginationOptionalClasspathTest {

    @Test
    void legacyAndDefaultReflectionFactoriesRemainLoadableWithoutPaginationDependency()
            throws Exception {
        List<URL> urls = new ArrayList<>();
        int removed = 0;
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            if (entry.contains("/mybatis-plus-jsqlparser/")) {
                removed++;
            } else {
                urls.add(Path.of(entry).toUri().toURL());
            }
        }
        assertEquals(1, removed);
        try (URLClassLoader isolated =
                new URLClassLoader(
                        urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader())) {
            assertThrows(
                    ClassNotFoundException.class,
                    () ->
                            Class.forName(
                                    "com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor",
                                    false,
                                    isolated));
            Class<?> utils =
                    Class.forName(
                            "com.yggdrasil.labs.mybatis.util.ReflectionUtils", true, isolated);
            assertNull(utils.getMethod("createPaginationInnerInterceptor").invoke(null));
            assertNull(
                    utils.getMethod("createConfigurationScopedPaginationInnerInterceptor")
                            .invoke(null));
            Class.forName(
                            "com.yggdrasil.labs.mybatis.config.MybatisPlusAutoConfiguration",
                            true,
                            isolated)
                    .getDeclaredMethods();
        }
    }
}
