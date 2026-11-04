package com.yggdrasil.labs.mybatis.page;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class ColaOptionalClasspathTest {

    @Test
    void mybatis_autoconfiguration_and_legacy_pagination_work_without_cola() throws Exception {
        List<URL> urls = new ArrayList<>();
        int removed = 0;
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            if (entry.contains("cola-component-dto")) {
                removed++;
            } else {
                urls.add(Path.of(entry).toUri().toURL());
            }
        }
        assertEquals(1, removed, "测试类路径应恰好包含一个 COLA DTO 制品");

        try (URLClassLoader isolated =
                new URLClassLoader(
                        urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader())) {
            Thread thread = Thread.currentThread();
            ClassLoader previous = thread.getContextClassLoader();
            thread.setContextClassLoader(isolated);
            try {
                assertThrows(
                        ClassNotFoundException.class,
                        () -> Class.forName("com.alibaba.cola.dto.PageResponse", false, isolated));

                Class<?> probe = Class.forName(ProbeConfiguration.class.getName(), true, isolated);
                Object application =
                        Class.forName("org.springframework.boot.SpringApplication", true, isolated)
                                .getConstructor(Class[].class)
                                .newInstance((Object) new Class<?>[] {probe});
                Object context =
                        application
                                .getClass()
                                .getMethod("run", String[].class)
                                .invoke(
                                        application,
                                        (Object) new String[] {"--spring.main.banner-mode=off"});
                try (AutoCloseable closeable = (AutoCloseable) context) {
                    Class<?> mapperType =
                            Class.forName(ProbeMapper.class.getName(), true, isolated);
                    Class<?> contextType =
                            Class.forName(
                                    "org.springframework.context.ApplicationContext",
                                    true,
                                    isolated);
                    Object mapper =
                            contextType
                                    .getMethod("getBean", Class.class)
                                    .invoke(context, mapperType);
                    var query = mapperType.getMethod("slowQuery");
                    query.setAccessible(true);
                    assertEquals(7, query.invoke(mapper));

                    Class<?> requestType =
                            Class.forName(
                                    "com.yggdrasil.labs.common.page.PageRequest", true, isolated);
                    Object request = requestType.getConstructor().newInstance();
                    Class<?> converter =
                            Class.forName(
                                    "com.yggdrasil.labs.mybatis.page.PageConverters",
                                    true,
                                    isolated);
                    Class<?> pageType =
                            Class.forName(
                                    "com.baomidou.mybatisplus.extension.plugins.pagination.Page",
                                    true,
                                    isolated);
                    Object page =
                            converter.getMethod("toMybatisPage", requestType).invoke(null, request);
                    assertNotNull(page);
                    assertSame(isolated, converter.getClassLoader());

                    Class<?> resultType =
                            Class.forName(
                                    "com.yggdrasil.labs.common.page.PageResult", true, isolated);
                    Object result =
                            converter
                                    .getMethod(
                                            "toPageResult",
                                            Class.forName(
                                                    "com.baomidou.mybatisplus.core.metadata.IPage",
                                                    true,
                                                    isolated))
                                    .invoke(null, page);
                    assertTrue(resultType.isInstance(result));
                    assertEquals(0L, resultType.getMethod("getTotalCount").invoke(result));
                    assertSame(isolated, pageType.getClassLoader());

                    Class<?> objectMapperType =
                            Class.forName(
                                    "com.fasterxml.jackson.databind.ObjectMapper", true, isolated);
                    Object objectMapper = objectMapperType.getConstructor().newInstance();
                    String json =
                            (String)
                                    objectMapperType
                                            .getMethod("writeValueAsString", Object.class)
                                            .invoke(objectMapper, request);
                    assertFalse(json.isBlank());
                    requestType.getDeclaredMethods();
                    converter.getDeclaredMethods();

                    Class<?> adapter =
                            Class.forName(
                                    "com.yggdrasil.labs.mybatis.page.IPageColaConverters",
                                    true,
                                    isolated);
                    assertThrows(LinkageError.class, adapter::getDeclaredMethods);
                }
            } finally {
                thread.setContextClassLoader(previous);
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({
        com.yggdrasil.labs.mybatis.config.MybatisPlusAutoConfiguration.class,
        com.yggdrasil.labs.mybatis.config.MybatisPlusLoggingConfiguration.class,
        com.yggdrasil.labs.mybatis.config.MybatisPlusSlowSqlConfiguration.class,
        com.yggdrasil.labs.mybatis.config.MybatisPlusAuditConfiguration.class,
        com.yggdrasil.labs.mybatis.config.MybatisPlusCryptoConfiguration.class,
        com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration.class
    })
    public static class ProbeConfiguration {

        @Bean
        DataSource dataSource() {
            SingleConnectionDataSource source = new SingleConnectionDataSource();
            source.setDriverClassName("org.h2.Driver");
            source.setUrl("jdbc:h2:mem:cola_optional_classpath");
            source.setSuppressClose(true);
            new JdbcTemplate(source)
                    .execute(
                            "CREATE ALIAS SLOW_ID FOR \""
                                    + ProbeConfiguration.class.getName()
                                    + ".pause\"");
            return source;
        }

        @Bean
        MapperFactoryBean<ProbeMapper> probeMapper(SqlSessionFactory factory) {
            MapperFactoryBean<ProbeMapper> mapper = new MapperFactoryBean<>(ProbeMapper.class);
            mapper.setSqlSessionFactory(factory);
            return mapper;
        }

        public static Integer pause(Integer value) throws InterruptedException {
            Thread.sleep(20);
            return value;
        }
    }

    interface ProbeMapper {
        @Select("SELECT SLOW_ID(7)")
        Integer slowQuery();
    }
}
