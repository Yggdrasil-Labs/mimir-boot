package com.yggdrasil.labs.rpc.core.tracing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import com.yggdrasil.labs.rpc.core.context.RpcCallContext;
import com.yggdrasil.labs.rpc.core.context.RpcCallMetadata;

class LegacyRpcTracerBridgeCompatibilityTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void shouldRestoreMdcChangedByLegacyExtractOnlyBridge() {
        MDC.put("legacy-trace", "previous");
        RpcTracerBridge bridge =
                new RpcTracerBridge() {
                    @Override
                    public Map<String, String> inject(RpcCallContext context) {
                        return Map.of();
                    }

                    @Override
                    public void extract(RpcCallContext context, Map<String, String> carrier) {
                        MDC.put("legacy-trace", carrier.get("legacy-trace"));
                    }
                };

        RpcTraceScope scope = bridge.extractScope(context(), Map.of("legacy-trace", "current"));
        assertThat(MDC.get("legacy-trace")).isEqualTo("current");

        scope.close();

        assertThat(MDC.get("legacy-trace")).isEqualTo("previous");
    }

    @Test
    void shouldLoadLegacyExtractOnlyBridge() {
        AtomicReference<Map<String, String>> extracted = new AtomicReference<>();
        RpcTracerBridge bridge =
                new RpcTracerBridge() {
                    @Override
                    public Map<String, String> inject(RpcCallContext context) {
                        return Map.of();
                    }

                    @Override
                    public void extract(RpcCallContext context, Map<String, String> carrier) {
                        extracted.set(carrier);
                    }
                };

        RpcTraceScope scope = bridge.extractScope(context(), Map.of("x-trace-id", "legacy"));
        scope.close();

        assertThat(extracted.get()).containsEntry("x-trace-id", "legacy");
    }

    @Test
    void shouldRestoreOnlyKeysAddedRemovedOrModifiedDuringExtraction() {
        MDC.put("modified", "previous");
        MDC.put("removed", "restore-me");
        MDC.put("unrelated", "before");
        RpcTracerBridge bridge =
                legacyBridge(
                        carrier -> {
                            MDC.put("modified", "current");
                            MDC.remove("removed");
                            MDC.put("added", "temporary");
                        });

        RpcTraceScope scope = bridge.extractScope(context(), Map.of());
        assertThat(MDC.get("modified")).isEqualTo("current");
        assertThat(MDC.get("removed")).isNull();
        assertThat(MDC.get("added")).isEqualTo("temporary");
        MDC.put("unrelated", "after");
        MDC.put("unrelated-new", "keep-me");

        scope.close();

        assertThat(MDC.get("modified")).isEqualTo("previous");
        assertThat(MDC.get("removed")).isEqualTo("restore-me");
        assertThat(MDC.get("added")).isNull();
        assertThat(MDC.get("unrelated")).isEqualTo("after");
        assertThat(MDC.get("unrelated-new")).isEqualTo("keep-me");
    }

    @Test
    void shouldRestoreEmptyMdcAfterExtraction() {
        MDC.clear();
        RpcTracerBridge bridge = legacyBridge(carrier -> MDC.put("added", "temporary"));

        RpcTraceScope scope = bridge.extractScope(context(), Map.of());
        scope.close();

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void shouldRestoreKeysClearedDuringExtraction() {
        MDC.put("first", "restore-first");
        MDC.put("second", "restore-second");
        RpcTracerBridge bridge = legacyBridge(carrier -> MDC.clear());

        RpcTraceScope scope = bridge.extractScope(context(), Map.of());
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
        scope.close();

        assertThat(MDC.getCopyOfContextMap())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("first", "restore-first", "second", "restore-second"));
    }

    @Test
    void shouldRollBackMdcAndPreserveExtractionException() {
        assertExtractionFailureRollsBack(new IllegalStateException("提取失败"));
    }

    @Test
    void shouldRollBackMdcAndPreserveExtractionError() {
        assertExtractionFailureRollsBack(new AssertionError("提取失败"));
    }

    @Test
    void shouldRestoreNestedScopesInReverseOrder() {
        MDC.put("legacy-trace", "original");
        RpcTracerBridge bridge =
                legacyBridge(carrier -> MDC.put("legacy-trace", carrier.get("legacy-trace")));

        RpcTraceScope outer = bridge.extractScope(context(), Map.of("legacy-trace", "outer"));
        RpcTraceScope inner = bridge.extractScope(context(), Map.of("legacy-trace", "inner"));
        assertThat(MDC.get("legacy-trace")).isEqualTo("inner");

        inner.close();
        assertThat(MDC.get("legacy-trace")).isEqualTo("outer");
        outer.close();
        assertThat(MDC.get("legacy-trace")).isEqualTo("original");
    }

    @Test
    void shouldCloseOnlyOnceWithoutOverwritingLaterMdcChanges() {
        MDC.put("legacy-trace", "original");
        RpcTracerBridge bridge = legacyBridge(carrier -> MDC.put("legacy-trace", "current"));

        RpcTraceScope scope = bridge.extractScope(context(), Map.of());
        scope.close();
        MDC.put("legacy-trace", "after-close");
        scope.close();

        assertThat(MDC.get("legacy-trace")).isEqualTo("after-close");
    }

    @Test
    void shouldRejectAnotherThreadWithoutConsumingOwnerClose() throws Exception {
        MDC.put("legacy-trace", "original");
        RpcTracerBridge bridge = legacyBridge(carrier -> MDC.put("legacy-trace", "current"));
        RpcTraceScope scope = bridge.extractScope(context(), Map.of());
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            worker.submit(
                            () -> {
                                MDC.put("legacy-trace", "worker");
                                assertThatThrownBy(scope::close)
                                        .isInstanceOf(IllegalStateException.class);
                                assertThat(MDC.get("legacy-trace")).isEqualTo("worker");
                            })
                    .get(5, TimeUnit.SECONDS);

            assertThat(MDC.get("legacy-trace")).isEqualTo("current");
            scope.close();
            assertThat(MDC.get("legacy-trace")).isEqualTo("original");
        } finally {
            worker.shutdownNow();
        }
    }

    @Test
    void shouldKeepScopesIndependentAcrossThreads() throws Exception {
        MDC.put("legacy-trace", "main-original");
        RpcTracerBridge bridge =
                legacyBridge(carrier -> MDC.put("legacy-trace", carrier.get("legacy-trace")));
        RpcTraceScope scope =
                bridge.extractScope(context(), Map.of("legacy-trace", "main-current"));
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            String workerTrace =
                    worker.submit(
                                    () -> {
                                        MDC.put("legacy-trace", "worker-original");
                                        RpcTraceScope workerScope =
                                                bridge.extractScope(
                                                        context(),
                                                        Map.of("legacy-trace", "worker-current"));
                                        assertThat(MDC.get("legacy-trace"))
                                                .isEqualTo("worker-current");
                                        workerScope.close();
                                        return MDC.get("legacy-trace");
                                    })
                            .get(5, TimeUnit.SECONDS);

            assertThat(workerTrace).isEqualTo("worker-original");
            assertThat(MDC.get("legacy-trace")).isEqualTo("main-current");
            scope.close();
            assertThat(MDC.get("legacy-trace")).isEqualTo("main-original");
        } finally {
            worker.shutdownNow();
        }
    }

    private void assertExtractionFailureRollsBack(Throwable failure) {
        MDC.put("modified", "previous");
        MDC.put("removed", "restore-me");
        RpcTracerBridge bridge =
                legacyBridge(
                        carrier -> {
                            MDC.put("modified", "current");
                            MDC.remove("removed");
                            MDC.put("added", "temporary");
                            if (failure instanceof RuntimeException exception) {
                                throw exception;
                            }
                            throw (Error) failure;
                        });

        assertThatThrownBy(() -> bridge.extractScope(context(), Map.of())).isSameAs(failure);

        assertThat(MDC.get("modified")).isEqualTo("previous");
        assertThat(MDC.get("removed")).isEqualTo("restore-me");
        assertThat(MDC.get("added")).isNull();
    }

    private RpcTracerBridge legacyBridge(Consumer<Map<String, String>> extraction) {
        return new RpcTracerBridge() {
            @Override
            public Map<String, String> inject(RpcCallContext context) {
                return Map.of();
            }

            @Override
            public void extract(RpcCallContext context, Map<String, String> carrier) {
                extraction.accept(carrier);
            }
        };
    }

    private RpcCallContext context() {
        return RpcCallContext.create(
                RpcCallMetadata.builder()
                        .service("legacy-service")
                        .method("legacy-method")
                        .protocol("test")
                        .target("test")
                        .build());
    }
}
