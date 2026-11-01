package com.yggdrasil.labs.common.page;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PageMappingCompilationTest {

    @Test
    void map_accepts_wildcard_function_with_serializable_output(@TempDir Path outputDirectory)
            throws Exception {
        CompilationResult result =
                compile(
                        "example.WildcardMapConsumer",
                        """
package example;

import com.yggdrasil.labs.common.page.PageResult;
import java.util.function.Function;

final class WildcardMapConsumer {
    void use(PageResult<Integer> page) {
        Function<Number, String> mapper = String::valueOf;
        PageResult<String> mapped = page.map(mapper);
        PageResult<CharSequenceHolder> holders = page.map(v -> new CharSequenceHolder());
    }
}

final class CharSequenceHolder implements java.io.Serializable {}
""",
                        outputDirectory);

        assertTrue(result.success(), result::diagnostics);
    }

    @Test
    void map_to_non_serializable_output_does_not_compile(@TempDir Path outputDirectory)
            throws Exception {
        CompilationResult result =
                compile(
                        "example.NonSerializableMapConsumer",
                        """
package example;

import com.yggdrasil.labs.common.page.PageResult;

final class Plain {}

final class NonSerializableMapConsumer {
    void use(PageResult<Integer> page) {
        page.map(v -> new Plain());
    }
}
""",
                        outputDirectory);

        assertFalse(result.success(), "PageResult.map 输出必须保持 Serializable 上界");
    }

    @Test
    void cola_conversion_allows_non_serializable_output(@TempDir Path outputDirectory)
            throws Exception {
        CompilationResult result =
                compile(
                        "example.ColaConsumer",
                        """
package example;

import com.alibaba.cola.dto.PageResponse;
import com.yggdrasil.labs.common.page.ColaPageConverters;
import com.yggdrasil.labs.common.page.PageResult;
import java.util.function.Function;

final class Plain {}

final class ColaConsumer {
    void use(PageResult<Integer> page) {
        Function<Object, Plain> mapper = v -> new Plain();
        PageResponse<Plain> plain = ColaPageConverters.toCola(page, mapper);
        PageResponse<Integer> same = ColaPageConverters.toCola(page);
    }
}
""",
                        outputDirectory);

        assertTrue(result.success(), result::diagnostics);
    }

    private CompilationResult compile(String className, String source, Path outputDirectory)
            throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "tests must run on a JDK");

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
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

    private record CompilationResult(boolean success, String diagnostics) {}

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
