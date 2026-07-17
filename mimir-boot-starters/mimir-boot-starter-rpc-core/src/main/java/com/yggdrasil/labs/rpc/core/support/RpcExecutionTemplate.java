package com.yggdrasil.labs.rpc.core.support;

import com.yggdrasil.labs.rpc.core.context.RpcCallContext;
import com.yggdrasil.labs.rpc.core.context.RpcCallResult;
import com.yggdrasil.labs.rpc.core.hook.RpcHookChain;
import com.yggdrasil.labs.rpc.core.tracing.RpcTracerBridge;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 简单的执行模板，用于在调用两侧调度 Hook 与上下文传递。
 *
 * <p>注意：模板不负责具体协议调用，仅调度扩展点。</p>
 */
public class RpcExecutionTemplate {

    private static final Logger log = LoggerFactory.getLogger(RpcExecutionTemplate.class);

    private final RpcHookChain hookChain;
    private final RpcTracerBridge tracerBridge;
    private final boolean contextPropagationEnabled;

    public RpcExecutionTemplate(RpcHookChain hookChain, RpcTracerBridge tracerBridge, boolean contextPropagationEnabled) {
        this.hookChain = hookChain;
        this.tracerBridge = tracerBridge;
        this.contextPropagationEnabled = contextPropagationEnabled;
        log.debug("RpcExecutionTemplate initialized, contextPropagationEnabled={}", contextPropagationEnabled);
    }

    /**
     * 执行调用（无返回值）。
     */
    public void execute(RpcCallContext context, Runnable runnable) {
        execute(context, () -> {
            runnable.run();
            return null;
        });
    }

    /**
     * 执行调用（有返回值）。
     */
    public <T> T execute(RpcCallContext context, Callable<T> callable) {
        if (log.isDebugEnabled()) {
            log.debug("Executing RPC call: service={}, method={}, contextPropagationEnabled={}",
                    context.getMetadata().getService(),
                    context.getMetadata().getMethod(),
                    contextPropagationEnabled);
        }
        Instant start = Instant.now();
        hookChain.before(context);
        if (contextPropagationEnabled) {
            Map<String, String> injected = tracerBridge.inject(context);
            if (log.isDebugEnabled() && injected != null && !injected.isEmpty()) {
                log.debug("Injected context propagation headers: {}", injected.keySet());
            }
            if (injected != null && !injected.isEmpty()) {
                injected.forEach(context::putAttachment);
            }
        }
        try {
            T result = callable.call();
            Duration duration = Duration.between(start, Instant.now());
            if (log.isDebugEnabled()) {
                log.debug("RPC call succeeded: service={}, method={}, duration={}ms",
                        context.getMetadata().getService(),
                        context.getMetadata().getMethod(),
                        duration.toMillis());
            }
            hookChain.after(context, RpcCallResult.success(duration));
            return result;
        } catch (Exception ex) {
            Duration duration = Duration.between(start, Instant.now());
            if (log.isDebugEnabled()) {
                log.debug("RPC call failed: service={}, method={}, duration={}ms, error={}",
                        context.getMetadata().getService(),
                        context.getMetadata().getMethod(),
                        duration.toMillis(),
                        ex.getClass().getSimpleName(),
                        ex);
            }
            hookChain.onError(context, RpcCallResult.failure(duration, ex));
            throw wrapIfNeeded(ex);
        } finally {
            hookChain.cleanup(context);
        }
    }

    private RuntimeException wrapIfNeeded(Exception ex) {
        if (ex instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new RuntimeException(ex);
    }
}
