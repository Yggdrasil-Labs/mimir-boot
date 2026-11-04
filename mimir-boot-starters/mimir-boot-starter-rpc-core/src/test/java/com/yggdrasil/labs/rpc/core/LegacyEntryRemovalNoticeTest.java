package com.yggdrasil.labs.rpc.core;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;

import com.yggdrasil.labs.rpc.core.context.RpcCallContext;
import com.yggdrasil.labs.rpc.core.context.RpcCallResult;
import com.yggdrasil.labs.rpc.core.hook.RpcHookChain;
import com.yggdrasil.labs.rpc.core.tracing.MdcRpcTracerBridge;
import com.yggdrasil.labs.rpc.core.tracing.NoopRpcTracerBridge;
import com.yggdrasil.labs.rpc.core.tracing.RpcTracerBridge;

/** 旧入口删除预告：注解、默认/严格编译告警、外部 Bridge 覆盖抑制及 README 迁移示例。 */
class LegacyEntryRemovalNoticeTest {

    private static final String REMOVAL_WARNING = "compiler.warn.has.been.deprecated.for.removal";

    @Test
    void legacy_entries_are_marked_for_removal_since_2_2_1() throws Exception {
        List<Method> legacy =
                List.of(
                        RpcTracerBridge.class.getMethod("extract", RpcCallContext.class, Map.class),
                        MdcRpcTracerBridge.class.getMethod(
                                "extract", RpcCallContext.class, Map.class),
                        NoopRpcTracerBridge.class.getMethod(
                                "extract", RpcCallContext.class, Map.class),
                        RpcHookChain.class.getMethod("before", RpcCallContext.class),
                        RpcHookChain.class.getMethod(
                                "after", RpcCallContext.class, RpcCallResult.class),
                        RpcHookChain.class.getMethod(
                                "onError", RpcCallContext.class, RpcCallResult.class),
                        RpcHookChain.class.getMethod("cleanup", RpcCallContext.class));

        for (Method method : legacy) {
            Deprecated deprecated = method.getAnnotation(Deprecated.class);
            assertNotNull(deprecated, method.toString());
            assertEquals("2.2.1", deprecated.since(), method.toString());
            assertTrue(deprecated.forRemoval(), method.toString());
        }
    }

    @Test
    void legacy_caller_compiles_with_removal_warning_and_fails_under_werror() throws Exception {
        String source =
                """
package example;

import com.yggdrasil.labs.rpc.core.context.RpcCallContext;
import com.yggdrasil.labs.rpc.core.context.RpcCallResult;
import com.yggdrasil.labs.rpc.core.hook.RpcHookChain;
import com.yggdrasil.labs.rpc.core.tracing.RpcTracerBridge;
import java.time.Duration;
import java.util.Map;

final class LegacyCaller {
    void call(RpcHookChain chain, RpcTracerBridge bridge, RpcCallContext context) {
        bridge.extract(context, Map.of());
        chain.before(context);
        chain.after(context, RpcCallResult.success(Duration.ZERO));
        chain.onError(context, RpcCallResult.failure(Duration.ZERO, new RuntimeException()));
        chain.cleanup(context);
    }
}
""";

        Result normal = compile("example.LegacyCaller", source, List.of());
        assertTrue(normal.success(), normal.diagnostics());
        assertEquals(5, normal.removalWarnings(), normal.diagnostics());

        Result strict = compile("example.LegacyCaller", source, List.of("-Werror"));
        assertFalse(strict.success(), "-Werror 下 removal 告警应导致编译失败");
    }

    @Test
    void external_bridge_override_needs_suppression_to_pass_werror() throws Exception {
        String unsuppressed = bridgeSource("UnsuppressedBridge", "");
        Result warned = compile("example.UnsuppressedBridge", unsuppressed, List.of());
        assertTrue(warned.success(), warned.diagnostics());
        assertEquals(1, warned.removalWarnings(), "仅迁移 extractScope 不能消除覆盖 extract 的告警");

        String deprecatedOnly =
                bridgeSource(
                        "DeprecatedOnlyBridge",
                        "    @Deprecated(since = \"2.2.1\", forRemoval = true)\n");
        assertFalse(
                compile("example.DeprecatedOnlyBridge", deprecatedOnly, List.of("-Werror"))
                        .success(),
                "仅标 @Deprecated 不豁免 removal 告警");

        String suppressed =
                bridgeSource(
                        "SuppressedBridge",
                        "    @SuppressWarnings(\"removal\")\n"
                                + "    @Deprecated(since = \"2.2.1\", forRemoval = true)\n");
        Result strict = compile("example.SuppressedBridge", suppressed, List.of("-Werror"));
        assertTrue(strict.success(), strict.diagnostics());
        assertEquals(0, strict.removalWarnings());
    }

    @Test
    void migrated_bridge_without_extract_override_passes_werror() throws Exception {
        String source =
                """
package example;

import com.yggdrasil.labs.rpc.core.context.RpcCallContext;
import com.yggdrasil.labs.rpc.core.tracing.RpcTraceScope;
import com.yggdrasil.labs.rpc.core.tracing.RpcTracerBridge;
import java.util.Map;

final class ScopeOnlyBridge implements RpcTracerBridge {
    @Override
    public Map<String, String> inject(RpcCallContext context) {
        return Map.of();
    }

    @Override
    public RpcTraceScope extractScope(RpcCallContext context, Map<String, String> carrier) {
        return RpcTraceScope.noop();
    }
}
""";
        Result strict = compile("example.ScopeOnlyBridge", source, List.of("-Werror"));
        assertTrue(strict.success(), strict.diagnostics());
        assertEquals(0, strict.removalWarnings());
    }

    @Test
    void readme_migration_examples_compile_strictly_and_clean_up() throws Exception {
        String source =
                """
package example;

import com.yggdrasil.labs.rpc.core.context.RpcCallContext;
import com.yggdrasil.labs.rpc.core.context.RpcCallMetadata;
import com.yggdrasil.labs.rpc.core.context.RpcCallResult;
import com.yggdrasil.labs.rpc.core.hook.RpcAsyncHookInvocation;
import com.yggdrasil.labs.rpc.core.hook.RpcHook;
import com.yggdrasil.labs.rpc.core.hook.RpcHookChain;
import com.yggdrasil.labs.rpc.core.hook.RpcHookInvocation;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class MigrationExamples {

    // README 同步示例
    static <T> T callSync(RpcHookChain hookChain, RpcCallContext context, Supplier<T> remoteCall) {
        Instant start = Instant.now();
        RpcHookInvocation invocation = hookChain.open(context);
        try {
            invocation.before();
            T value = remoteCall.get();
            invocation.completeSuccess(
                    RpcCallResult.success(Duration.between(start, Instant.now())));
            return value;
        } catch (RuntimeException error) {
            // primaryError 与 RpcCallResult.failure 中的异常为同一非 null 实例，完成后由调用方重抛
            invocation.completeFailure(
                    RpcCallResult.failure(Duration.between(start, Instant.now()), error), error);
            throw error;
        } finally {
            invocation.close();
        }
    }

    // README 异步示例：终态交给完成回调，不使用 try-with-resources
    static <T> CompletableFuture<T> callAsync(
            RpcHookChain hookChain,
            RpcCallContext context,
            Supplier<CompletableFuture<T>> remoteCall) {
        Instant start = Instant.now();
        RpcAsyncHookInvocation invocation = hookChain.openAsync(context);
        CompletableFuture<T> future;
        try {
            invocation.before();
            future = remoteCall.get();
        } catch (RuntimeException error) {
            invocation.completeFailure(
                    RpcCallResult.failure(Duration.between(start, Instant.now()), error), error);
            throw error;
        }
        try {
            return future.whenComplete(
                    (value, error) -> {
                        Duration duration = Duration.between(start, Instant.now());
                        if (error == null) {
                            invocation.completeSuccess(RpcCallResult.success(duration));
                        } else {
                            invocation.completeFailure(
                                    RpcCallResult.failure(duration, error), error);
                        }
                    });
        } catch (RuntimeException registrationFailure) {
            // 回调未注册成功：以实际异常结束生命周期，避免泄漏
            invocation.completeFailure(
                    RpcCallResult.failure(
                            Duration.between(start, Instant.now()), registrationFailure),
                    registrationFailure);
            throw registrationFailure;
        }
    }

    public static List<String> run() {
        List<String> events = new ArrayList<>();
        RpcHook hook =
                new RpcHook() {
                    @Override
                    public void before(RpcCallContext context) {
                        events.add("before");
                    }

                    @Override
                    public void after(RpcCallContext context, RpcCallResult result) {
                        events.add("after");
                    }

                    @Override
                    public void onError(RpcCallContext context, RpcCallResult result) {
                        events.add("onError");
                    }

                    @Override
                    public void cleanup(RpcCallContext context) {
                        events.add("cleanup");
                    }
                };
        RpcHookChain chain = new RpcHookChain(List.of(hook));
        RpcCallContext context =
                RpcCallContext.create(RpcCallMetadata.builder().service("s").method("m").build());

        callSync(chain, context, () -> "ok");
        events.add("|");
        IllegalStateException boom = new IllegalStateException("boom");
        try {
            callSync(chain, context, () -> { throw boom; });
        } catch (IllegalStateException e) {
            events.add(e == boom ? "rethrown" : "other");
        }
        events.add("|");
        CompletableFuture<String> pending = new CompletableFuture<>();
        CompletableFuture<String> result = callAsync(chain, context, () -> pending);
        events.add("returned");
        pending.complete("ok");
        result.join();
        events.add("|");
        CompletableFuture<String> failing = new CompletableFuture<>();
        CompletableFuture<String> failed = callAsync(chain, context, () -> failing);
        failing.completeExceptionally(boom);
        events.add(failed.isCompletedExceptionally() ? "asyncFailed" : "asyncNotFailed");
        return events;
    }
}
""";

        Path output = Files.createTempDirectory("rpc-migration-");
        Result strict = compile("example.MigrationExamples", source, List.of("-Werror"), output);
        assertTrue(strict.success(), strict.diagnostics());

        try (URLClassLoader loader =
                new URLClassLoader(
                        new URL[] {output.toUri().toURL()}, getClass().getClassLoader())) {
            @SuppressWarnings("unchecked")
            List<String> events =
                    (List<String>)
                            Class.forName("example.MigrationExamples", true, loader)
                                    .getMethod("run")
                                    .invoke(null);

            assertEquals(
                    List.of(
                            "before",
                            "after",
                            "cleanup",
                            "|",
                            "before",
                            "onError",
                            "cleanup",
                            "rethrown",
                            "|",
                            "before",
                            "returned",
                            "after",
                            "cleanup",
                            "|",
                            "before",
                            "onError",
                            "cleanup",
                            "asyncFailed"),
                    events);
        }
    }

    private static String bridgeSource(String name, String annotations) {
        return """
package example;

import com.yggdrasil.labs.rpc.core.context.RpcCallContext;
import com.yggdrasil.labs.rpc.core.tracing.RpcTraceScope;
import com.yggdrasil.labs.rpc.core.tracing.RpcTracerBridge;
import java.util.Map;

final class %s implements RpcTracerBridge {
    @Override
    public Map<String, String> inject(RpcCallContext context) {
        return Map.of();
    }

    @Override
%s    public void extract(RpcCallContext context, Map<String, String> carrier) {
        extractScope(context, carrier).close();
    }

    @Override
    public RpcTraceScope extractScope(RpcCallContext context, Map<String, String> carrier) {
        return RpcTraceScope.noop();
    }
}
"""
                .formatted(name, annotations);
    }

    private Result compile(String className, String source, List<String> options) throws Exception {
        return compile(className, source, options, Files.createTempDirectory("rpc-removal-"));
    }

    private Result compile(String className, String source, List<String> options, Path output)
            throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "tests must run on a JDK");
        List<String> args = new ArrayList<>(options);
        args.addAll(
                List.of(
                        "-proc:none",
                        "-classpath",
                        System.getProperty("java.class.path"),
                        "-d",
                        output.toString()));

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager =
                compiler.getStandardFileManager(diagnostics, null, null)) {
            boolean success =
                    compiler.getTask(
                                    null,
                                    fileManager,
                                    diagnostics,
                                    args,
                                    null,
                                    List.of(new SourceFile(className, source)))
                            .call();
            long removal =
                    diagnostics.getDiagnostics().stream()
                            .filter(
                                    d ->
                                            d.getKind() == Diagnostic.Kind.WARNING
                                                    || d.getKind()
                                                            == Diagnostic.Kind.MANDATORY_WARNING)
                            .filter(d -> REMOVAL_WARNING.equals(d.getCode()))
                            .count();
            return new Result(success, (int) removal, diagnostics.getDiagnostics().toString());
        }
    }

    private record Result(boolean success, int removalWarnings, String diagnostics) {}

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
