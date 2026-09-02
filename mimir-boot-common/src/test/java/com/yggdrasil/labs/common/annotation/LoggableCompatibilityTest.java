package com.yggdrasil.labs.common.annotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

class LoggableCompatibilityTest {

    // 仅兼容性契约（反射、编译与链接）测试刻意引用 forRemoval API；生产代码没有运行时消费者。
    private static final String LEGACY_FIXTURE_ROOT = "/compatibility/loggable-pre-v2.2.1/";

    @SuppressWarnings({"removal", "java:S5738"})
    @Test
    void preservesAllAnnotationMembersAndDefaults() throws Exception {
        Deprecated deprecated = Loggable.class.getAnnotation(Deprecated.class);

        assertNotNull(deprecated);

        assertEquals("2.2.1", deprecated.since());
        assertTrue(deprecated.forRemoval());
        assertEquals("", Loggable.class.getMethod("module").getDefaultValue());
        assertEquals("", Loggable.class.getMethod("type").getDefaultValue());
        assertEquals("", Loggable.class.getMethod("description").getDefaultValue());
        assertEquals(true, Loggable.class.getMethod("logRequest").getDefaultValue());
        assertEquals(true, Loggable.class.getMethod("logResponse").getDefaultValue());
        assertEquals(true, Loggable.class.getMethod("logExecutionTime").getDefaultValue());
    }

    @SuppressWarnings({"removal", "java:S5738"})
    @Test
    void compilesConsumerWithDeprecationDiagnostic() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path directory = Files.createTempDirectory("loggable-compat-");
        Path source = directory.resolve("Consumer.java");
        Files.writeString(source, "import com.yggdrasil.labs.common.annotation.Loggable; @Loggable class Consumer {}");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        String classpath = Path.of(Loggable.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();

        boolean compiled = compiler.getTask(
                        null,
                        null,
                        diagnostics,
                        List.of("-classpath", classpath, "-Xlint:deprecation", "-d", directory.toString()),
                        null,
                        compiler.getStandardFileManager(diagnostics, null, null).getJavaFileObjects(source.toFile()))
                .call();

        assertTrue(compiled);
        assertTrue(diagnostics.getDiagnostics().stream().anyMatch(diagnostic -> diagnostic.getMessage(null).contains("Loggable")));
    }

    @Test
    void documentsDeprecationMigrationWithoutRuntimePromise() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/yggdrasil/labs/common/annotation/Loggable.java"));
        String readme = Files.readString(Path.of("README.md"));

        assertTrue(source.contains("当前无内置运行时消费者"));
        assertTrue(source.contains("计划于 3.0 移除"));
        assertTrue(readme.contains("当前无内置运行时消费者"));
        assertTrue(readme.contains("计划于 3.0 移除"));
        assertFalse(source.contains("自动记录日志"));
        assertFalse(readme.contains("自动记录日志"));
    }

    @SuppressWarnings({"removal", "java:S5738"})
    @Test
    void linksLegacyConsumerAgainstCurrentLoggable() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "兼容性测试需要 JDK JavaCompiler");
        Path directory = Files.createTempDirectory("loggable-legacy-");
        Path sourceRoot = directory.resolve("sources");
        Path classes = directory.resolve("classes");
        Path annotation = sourceRoot.resolve("com/yggdrasil/labs/common/annotation/Loggable.java");
        Path consumer = sourceRoot.resolve("com/yggdrasil/labs/common/compat/PrecompiledLoggableConsumer.java");
        copyFixture("com/yggdrasil/labs/common/annotation/Loggable.java", annotation);
        copyFixture("com/yggdrasil/labs/common/compat/PrecompiledLoggableConsumer.java", consumer);

        Files.createDirectories(classes);
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            boolean compiled = compiler.getTask(
                            null,
                            fileManager,
                            diagnostics,
                            List.of("-d", classes.toString()),
                            null,
                            fileManager.getJavaFileObjects(annotation.toFile(), consumer.toFile()))
                    .call();
            assertTrue(compiled, diagnostics.getDiagnostics().toString());
        }
        Files.delete(classes.resolve("com/yggdrasil/labs/common/annotation/Loggable.class"));

        try (URLClassLoader loader = new URLClassLoader(new URL[] {classes.toUri().toURL()}, Loggable.class.getClassLoader())) {
            Class<?> consumerType = Class.forName("com.yggdrasil.labs.common.compat.PrecompiledLoggableConsumer", true, loader);
            Object legacyConsumer = consumerType.getDeclaredConstructor().newInstance();

            assertEquals("legacy", consumerType.getMethod("module").invoke(legacyConsumer));
        }
    }

    private static void copyFixture(String resourcePath, Path destination) throws IOException {
        Files.createDirectories(destination.getParent());
        try (InputStream input = LoggableCompatibilityTest.class.getResourceAsStream(LEGACY_FIXTURE_ROOT + resourcePath)) {
            assertNotNull(input, "缺少兼容性 fixture: " + resourcePath);
            Files.copy(input, destination);
        }
    }
}
