package com.yggdrasil.labs.rpc.core.tracing;

import com.yggdrasil.labs.rpc.core.context.RpcCallContext;
import com.yggdrasil.labs.rpc.core.context.RpcCallMetadata;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class NoopRpcTracerBridgeTest {

    @Test
    void shouldReturnEmptyMapAndDoNothingOnExtract() {
        NoopRpcTracerBridge bridge = new NoopRpcTracerBridge();
        RpcCallContext context = RpcCallContext.create(
                RpcCallMetadata.builder().service("svc").method("m1").build());

        Map<String, String> injected = bridge.inject(context);
        Assertions.assertNotNull(injected);
        Assertions.assertTrue(injected.isEmpty());

        Assertions.assertDoesNotThrow(() -> bridge.extract(context, Map.of("ignored", "true")));
    }
}
