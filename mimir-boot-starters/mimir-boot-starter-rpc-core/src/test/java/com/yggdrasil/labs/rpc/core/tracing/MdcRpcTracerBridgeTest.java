package com.yggdrasil.labs.rpc.core.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import com.yggdrasil.labs.common.constant.CommonConstants;
import com.yggdrasil.labs.common.constant.HttpHeaderConstants;
import com.yggdrasil.labs.rpc.core.context.RpcCallContext;
import com.yggdrasil.labs.rpc.core.context.RpcCallMetadata;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MdcRpcTracerBridgeTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void extractScopeReplacesInvalidTraceRemovesInvalidRequestAndRestoresOnlyOwnedKeys() {
        MdcRpcTracerBridge bridge = new MdcRpcTracerBridge();
        MDC.put(CommonConstants.TRACE_ID, "previous-trace");
        MDC.put(CommonConstants.REQUEST_ID, "previous-request");
        MDC.put("external", "keep-me");

        RpcTraceScope scope = bridge.extractScope(context(), Map.of(
                HttpHeaderConstants.TRACE_ID_HEADER, "invalid trace id",
                HttpHeaderConstants.REQUEST_ID_HEADER, "invalid request id"));

        assertThat(MDC.get(CommonConstants.TRACE_ID)).matches("[0-9a-f]{32}");
        assertThat(MDC.get(CommonConstants.REQUEST_ID)).isNull();
        assertThat(MDC.get("external")).isEqualTo("keep-me");

        scope.close();
        MDC.put("external", "changed-after-close");
        scope.close();

        assertThat(MDC.get(CommonConstants.TRACE_ID)).isEqualTo("previous-trace");
        assertThat(MDC.get(CommonConstants.REQUEST_ID)).isEqualTo("previous-request");
        assertThat(MDC.get("external")).isEqualTo("changed-after-close");
    }

    @Test
    void injectsOnlyValidTraceAndRequestIdentifiers() {
        MdcRpcTracerBridge bridge = new MdcRpcTracerBridge();
        MDC.put(CommonConstants.TRACE_ID, "valid-trace-id");
        MDC.put(CommonConstants.REQUEST_ID, "invalid request id");

        Map<String, String> carrier = bridge.inject(context());

        assertThat(carrier).containsEntry(HttpHeaderConstants.TRACE_ID_HEADER, "valid-trace-id");
        assertThat(carrier).doesNotContainKey(HttpHeaderConstants.REQUEST_ID_HEADER);
    }

    private RpcCallContext context() {
        return RpcCallContext.create(RpcCallMetadata.builder()
                .service("test-service")
                .method("test-method")
                .protocol("test")
                .target("test-target")
                .build());
    }
}
