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
     *
     * @deprecated 请实现 {@link #extractScope(RpcCallContext, Map)} 以获得可关闭的调用级上下文作用域；旧实现仍可加载，
     *     但默认 noop scope 不保证恢复其未知上下文。
     */
    @Deprecated(since = "2.2.1", forRemoval = false)
    void extract(RpcCallContext context, Map<String, String> carrier);

    /**
     * 从载体提取上下文，并返回当前调用结束时需要关闭的作用域。
     *
     * <p>保留默认实现以兼容仅实现 {@link #extract(RpcCallContext, Map)} 的既有 Bridge。</p>
     */
    default RpcTraceScope extractScope(RpcCallContext context, Map<String, String> carrier) {
        extract(context, carrier);
        return RpcTraceScope.noop();
    }
}
