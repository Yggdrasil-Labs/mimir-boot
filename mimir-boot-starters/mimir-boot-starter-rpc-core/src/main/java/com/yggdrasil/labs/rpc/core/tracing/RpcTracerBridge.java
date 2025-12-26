package com.yggdrasil.labs.rpc.core.tracing;

import com.yggdrasil.labs.rpc.core.context.RpcCallContext;
import java.util.Map;

/**
 * Trace/Span/Request-Id 的桥接接口，供外部 Tracing 实现覆盖。
 */
public interface RpcTracerBridge {

    /**
     * 将上下文注入到可传播的载体（如 header）。
     */
    Map<String, String> inject(RpcCallContext context);

    /**
     * 从载体提取上下文。
     */
    void extract(RpcCallContext context, Map<String, String> carrier);
}

