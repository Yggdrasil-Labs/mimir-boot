package com.yggdrasil.labs.test.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeprecatedApiCompilationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void legacyAutoConfigurationCanCompileWithDeprecationWarning() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "运行测试需要 JDK JavaCompiler");

        Path sourceFile = temporaryDirectory.resolve("LegacyConsumer.java");
        Files.writeString(sourceFile, """
                package consumer;
                import com.yggdrasil.labs.test.config.TestAutoConfiguration;
                public class LegacyConsumer {
                    private final TestAutoConfiguration configuration = new TestAutoConfiguration();
                }
                """);

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, null)) {
            Iterable<? extends JavaFileObject> compilationUnits =
                    fileManager.getJavaFileObjectsFromPaths(List.of(sourceFile));
            boolean compiled = compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    List.of(
                            "--release", "17",
                            "-classpath", System.getProperty("java.class.path"),
                            "-d", temporaryDirectory.resolve("classes").toString(),
                            "-Xlint:deprecation"),
                    null,
                    compilationUnits).call();

            assertTrue(compiled, () -> "旧自动配置应保持可编译：" + diagnostics.getDiagnostics());
        }

        assertTrue(diagnostics.getDiagnostics().stream().anyMatch(this::isTestAutoConfigurationDeprecation),
                () -> "应报告 TestAutoConfiguration 弃用诊断：" + diagnostics.getDiagnostics());
    }

    private boolean isTestAutoConfigurationDeprecation(Diagnostic<? extends JavaFileObject> diagnostic) {
        return (diagnostic.getKind() == Diagnostic.Kind.MANDATORY_WARNING
                        || diagnostic.getKind() == Diagnostic.Kind.WARNING)
                && diagnostic.getMessage(Locale.ROOT).contains("TestAutoConfiguration");
    }
}
