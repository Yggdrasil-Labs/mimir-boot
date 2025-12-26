package com.yggdrasil.labs.rpc.core.tracing;

import com.yggdrasil.labs.rpc.core.context.RpcCallContext;
import java.util.Collections;
import java.util.Map;

/**
 * 默认的 Trace 桥接空实现，保持轻量且可被覆盖。
 */
public class NoopRpcTracerBridge implements RpcTracerBridge {

    @Override
    public Map<String, String> inject(RpcCallContext context) {
        return Collections.emptyMap();
    }

    @Override
    public void extract(RpcCallContext context, Map<String, String> carrier) {
        // no-op
    }
}

