package com.yggdrasil.labs.common;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/** 通过真实 Java 编译器验证公共泛型约束。 */
public final class CompilationSupport {

    private CompilationSupport() {}

    public static CompilationResult compile(String className, String source, Path outputDirectory)
            throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "tests must run on a JDK");

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        // 文件管理器及时关闭；生成的 class 交由调用方的 @TempDir 在测试结束后清理。
        try (StandardJavaFileManager fileManager =
                compiler.getStandardFileManager(diagnostics, null, null)) {
            boolean success =
                    compiler.getTask(
                                    null,
                                    fileManager,
                                    diagnostics,
                                    List.of(
                                            "-classpath",
                                            System.getProperty("java.class.path"),
                                            "-d",
                                            outputDirectory.toString(),
                                            "-proc:none"),
                                    null,
                                    List.of(new SourceFile(className, source)))
                            .call();
            return new CompilationResult(success, diagnostics.getDiagnostics().toString());
        }
    }

    public record CompilationResult(boolean success, String diagnostics) {}

    private static final class SourceFile extends SimpleJavaFileObject {

        private final String source;

        private SourceFile(String className, String source) {
            super(
                    URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension),
                    Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }
}
