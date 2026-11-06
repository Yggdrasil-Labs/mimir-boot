package com.yggdrasil.labs.rpc.core.tracing;

/** RPC Trace 上下文在当前调用线程的生命周期。 */
@FunctionalInterface
public interface RpcTraceScope extends AutoCloseable {

    @Override
    void close();

    /** 返回无需恢复任何上下文的空作用域。 */
    static RpcTraceScope noop() {
        return () -> {
            // 无需恢复上下文。
        };
    }
}
