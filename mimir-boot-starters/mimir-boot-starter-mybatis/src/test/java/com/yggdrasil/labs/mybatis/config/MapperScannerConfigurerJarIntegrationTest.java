package com.yggdrasil.labs.mybatis.config;

import com.yggdrasil.labs.mybatis.util.MapperPackageDetector;
import org.junit.jupiter.api.io.TempDir;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.mybatis.spring.mapper.MapperScannerConfigurer;

import java.io.IOException;
import org.springframework.context.support.GenericApplicationContext;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URI;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import java.util.List;

class MapperScannerConfigurerJarIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    @org.junit.jupiter.api.Test
    void discoversMapperFromExecutableJarAndRegistersBean() throws Exception {
        Path jarFile = createMapperJar();
        ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader jarClassLoader = new URLClassLoader(
                new URL[] {jarFile.toUri().toURL()}, previousClassLoader)) {
            Thread.currentThread().setContextClassLoader(jarClassLoader);

            Set<String> detectedPackages = MapperPackageDetector.detectMapperPackages();
            assertTrue(detectedPackages.contains("org.example.order.mapper.**"));

            MybatisProperties properties = new MybatisProperties();
            String effectivePackages = properties.getEffectiveMapperPackages();
            assertTrue(effectivePackages.contains("org.example.order.mapper.**"));

            GenericApplicationContext context = new GenericApplicationContext();
            context.setClassLoader(jarClassLoader);
            try {
                Configuration configuration = new Configuration(new Environment("test",
                        new JdbcTransactionFactory(), new UnpooledDataSource()));
                SqlSessionFactory sqlSessionFactory = new DefaultSqlSessionFactory(configuration);
                MapperScannerConfigurer configurer = new MybatisPlusAutoConfiguration()
                        .mapperScannerConfigurer(properties);
                configurer.setSqlSessionFactory(sqlSessionFactory);
                configurer.setApplicationContext(context);
                configurer.setBeanName("orderMapperScanner");
                String basePackage = readBasePackage(configurer);
                assertTrue(basePackage.contains("org.example.order.mapper"));
                assertFalse(basePackage.contains("org.example.order.mapper.**"));
                configurer.postProcessBeanDefinitionRegistry(context);
                context.refresh();

                Class<?> mapperType = Class.forName("org.example.order.mapper.OrderMapper", true, jarClassLoader);
                Object mapper = context.getBean(mapperType);
                assertTrue(context.containsBean("orderMapper"));
                assertTrue("ok".equals(mapperType.getMethod("probe").invoke(mapper)));
            } finally {
                context.close();
            }
        } finally {
            Thread.currentThread().setContextClassLoader(previousClassLoader);
        }
    }

    private Path createMapperJar() throws IOException {
        Path compiledClasses = temporaryDirectory.resolve("compiled-classes");
        compileMapper(compiledClasses);
        byte[] mapperClass = Files.readAllBytes(
                compiledClasses.resolve("org/example/order/mapper/OrderMapper.class"));
        Path jarFile = temporaryDirectory.resolve("order-mappers.jar");
        try (JarOutputStream outputStream = new JarOutputStream(Files.newOutputStream(jarFile))) {
            writeDirectoryEntry(outputStream, "org/");
            writeDirectoryEntry(outputStream, "org/example/");
            writeDirectoryEntry(outputStream, "org/example/order/");
            writeDirectoryEntry(outputStream, "org/example/order/mapper/");
            writeEntry(outputStream, "org/example/order/mapper/OrderMapper.class", mapperClass);
            writeEntry(outputStream, "BOOT-INF/classes/org/example/order/mapper/OrderMapper.class", mapperClass);
        }
        return jarFile;
    }

    private void compileMapper(Path compiledClasses) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertTrue(compiler != null, "当前 JDK 必须提供 JavaCompiler");
        Files.createDirectories(compiledClasses);
        JavaFileObject source = new SimpleJavaFileObject(
                URI.create("string:///org/example/order/mapper/OrderMapper.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return "package org.example.order.mapper;"
                        + "import org.apache.ibatis.annotations.Mapper;"
                        + "@Mapper public interface OrderMapper { default String probe() { return \"ok\"; } }";
            }
        };
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(compiledClasses));
            assertTrue(compiler.getTask(null, fileManager, null,
                    List.of("-classpath", System.getProperty("java.class.path")), null, List.of(source)).call());
        }
    }

    private void writeEntry(JarOutputStream outputStream, String name, byte[] content) throws IOException {
        outputStream.putNextEntry(new JarEntry(name));
        outputStream.write(content);
        outputStream.closeEntry();
    }

    private void writeDirectoryEntry(JarOutputStream outputStream, String name) throws IOException {
        outputStream.putNextEntry(new JarEntry(name));
        outputStream.closeEntry();
    }

    private String readBasePackage(MapperScannerConfigurer configurer) throws Exception {
        Field field = MapperScannerConfigurer.class.getDeclaredField("basePackage");
        field.setAccessible(true);
        return (String) field.get(configurer);
    }
}
