package com.yggdrasil.labs.rpc.core.tracing;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.slf4j.MDC;

import com.yggdrasil.labs.rpc.core.context.RpcCallContext;

/** 旧 Bridge 提取逻辑的 MDC 兼容作用域，只恢复提取期间净变化的键。 */
final class LegacyMdcTraceScope implements RpcTraceScope {

    private final Map<String, String> previous;
    private final Set<String> changedKeys;
    private final Thread owner = Thread.currentThread();
    private boolean closed;

    private LegacyMdcTraceScope(Map<String, String> previous) {
        this.previous = previous;
        Map<String, String> current = snapshot();
        Set<String> keys = new HashSet<>(previous.keySet());
        keys.addAll(current.keySet());
        keys.removeIf(
                key ->
                        previous.containsKey(key) == current.containsKey(key)
                                && Objects.equals(previous.get(key), current.get(key)));
        this.changedKeys = keys;
    }

    static RpcTraceScope extract(
            RpcTracerBridge bridge, RpcCallContext context, Map<String, String> carrier) {
        Map<String, String> previous = snapshot();
        try {
            bridge.extract(context, carrier);
        } catch (RuntimeException | Error failure) {
            try {
                new LegacyMdcTraceScope(previous).close();
            } catch (RuntimeException | Error rollbackFailure) {
                if (rollbackFailure != failure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            throw failure;
        }
        return new LegacyMdcTraceScope(previous);
    }

    @Override
    public void close() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("旧 RPC MDC 作用域必须在创建线程关闭");
        }
        if (closed) {
            return;
        }
        closed = true;
        for (String key : changedKeys) {
            if (previous.containsKey(key)) {
                MDC.put(key, previous.get(key));
            } else {
                MDC.remove(key);
            }
        }
    }

    private static Map<String, String> snapshot() {
        Map<String, String> values = MDC.getCopyOfContextMap();
        return values == null ? Map.of() : values;
    }
}
