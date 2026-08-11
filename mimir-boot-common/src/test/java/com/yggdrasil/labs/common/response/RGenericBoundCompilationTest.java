package com.yggdrasil.labs.common.response;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RGenericBoundCompilationTest {

    @Test
    void serializable_payload_compiles_against_r(@TempDir Path outputDirectory) throws Exception {
        CompilationResult result = compile(
                "example.SerializableConsumer",
                """
                package example;

                import com.yggdrasil.labs.common.response.R;
                import java.io.Serializable;

                final class SerializablePayload implements Serializable {}

                final class SerializableConsumer {
                    private final R<SerializablePayload> response = R.success(new SerializablePayload());
                }
                """,
                outputDirectory);

        assertTrue(result.success(), result::diagnostics);
    }

    @Test
    void non_serializable_payload_does_not_compile_against_r(@TempDir Path outputDirectory) throws Exception {
        CompilationResult result = compile(
                "example.NonSerializableConsumer",
                """
                package example;

                import com.yggdrasil.labs.common.response.R;

                final class NonSerializablePayload {}

                final class NonSerializableConsumer {
                    private final R<NonSerializablePayload> response = R.success(new NonSerializablePayload());
                }
                """,
                outputDirectory);

        assertFalse(result.success(), "non-Serializable payload must remain a compile-time error");
    }

    private CompilationResult compile(String className, String source, Path outputDirectory) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "tests must run on a JDK");

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            boolean success = compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    List.of("-classpath", System.getProperty("java.class.path"), "-d", outputDirectory.toString(), "-proc:none"),
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
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }
}
