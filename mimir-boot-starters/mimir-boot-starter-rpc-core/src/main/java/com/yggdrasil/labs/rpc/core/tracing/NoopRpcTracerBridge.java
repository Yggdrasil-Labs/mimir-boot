package com.yggdrasil.labs.rpc.core.tracing;

import java.util.Collections;
import java.util.Map;

import com.yggdrasil.labs.rpc.core.context.RpcCallContext;

/** 默认的 Trace 桥接空实现，保持轻量且可被覆盖。 */
public class NoopRpcTracerBridge implements RpcTracerBridge {

    @Override
    public Map<String, String> inject(RpcCallContext context) {
        return Collections.emptyMap();
    }

    /**
     * @deprecated 请使用 {@link #extractScope(RpcCallContext, Map)}；计划于下一个主版本随接口旧入口一并删除。
     */
    @Override
    @SuppressWarnings("removal")
    @Deprecated(since = "2.2.1", forRemoval = true)
    public void extract(RpcCallContext context, Map<String, String> carrier) {
        // no-op
    }
}
