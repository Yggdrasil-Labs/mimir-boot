package com.yggdrasil.labs.rpc.core.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import com.yggdrasil.labs.rpc.core.context.RpcCallContext;
import com.yggdrasil.labs.rpc.core.context.RpcCallMetadata;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LegacyRpcTracerBridgeCompatibilityTest {

    @Test
    void shouldLoadLegacyExtractOnlyBridgeWithNoopScope() {
        AtomicReference<Map<String, String>> extracted = new AtomicReference<>();
        RpcTracerBridge bridge = new RpcTracerBridge() {
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

    private RpcCallContext context() {
        return RpcCallContext.create(RpcCallMetadata.builder()
                .service("legacy-service")
                .method("legacy-method")
                .protocol("test")
                .target("test")
                .build());
    }
}
