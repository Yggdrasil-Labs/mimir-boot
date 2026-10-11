package com.yggdrasil.labs.rpc.core.tracing;

import java.util.Map;

import com.yggdrasil.labs.rpc.core.context.RpcCallContext;

/** Trace/Span/Request-Id 的桥接接口，供外部 Tracing 实现覆盖。 */
public interface RpcTracerBridge {

    /** 将上下文注入到可传播的载体（如 header）。 */
    Map<String, String> inject(RpcCallContext context);

    /**
     * 从载体提取上下文。
     *
     * @deprecated 请实现 {@link #extractScope(RpcCallContext, Map)} 以获得可关闭的调用级上下文作用域；旧实现仍可加载，
     *     默认作用域仅恢复提取时修改的 MDC 键，无法恢复未知的自定义上下文。
     */
    @Deprecated(since = "2.2.1", forRemoval = false)
    void extract(RpcCallContext context, Map<String, String> carrier);

    /**
     * 从载体提取上下文，并返回当前调用结束时需要关闭的作用域。
     *
     * <p>保留默认实现以兼容仅实现 {@link #extract(RpcCallContext, Map)} 的既有 Bridge。默认作用域只恢复提取时修改的 MDC
     * 键，提取失败时先回滚再抛出原异常；必须在创建作用域的线程关闭，不恢复未知的 Span 或 ThreadLocal。
     */
    default RpcTraceScope extractScope(RpcCallContext context, Map<String, String> carrier) {
        return LegacyMdcTraceScope.extract(this, context, carrier);
    }
}
