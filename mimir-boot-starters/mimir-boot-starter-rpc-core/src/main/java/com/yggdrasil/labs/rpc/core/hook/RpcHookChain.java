package com.yggdrasil.labs.rpc.core.hook;

import com.yggdrasil.labs.rpc.core.context.RpcCallContext;
import com.yggdrasil.labs.rpc.core.context.RpcCallResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

/**
 * Hook 链调度器，按顺序执行 before/after/onError/cleanup。
 */
public class RpcHookChain {

    private static final Logger log = LoggerFactory.getLogger(RpcHookChain.class);

    private final List<RpcHook> hooks;

    public RpcHookChain(List<RpcHook> hooks) {
        if (hooks == null || hooks.isEmpty()) {
            this.hooks = Collections.emptyList();
            log.debug("RpcHookChain initialized with empty hooks");
        } else {
            List<RpcHook> sorted = new ArrayList<>(hooks);
            sorted.sort(Comparator.nullsLast(AnnotationAwareOrderComparator.INSTANCE));
            this.hooks = Collections.unmodifiableList(sorted);
            log.debug("RpcHookChain initialized with {} hooks", hooks.size());
        }
    }

    /**
     * 创建调用级生命周期状态，禁止在单例 Chain 上保存每次调用的 entered Hook。
     */
    public RpcHookInvocation open(RpcCallContext context) {
        return new RpcHookInvocation(context, hooks);
    }

    /**
     * 创建可把终态所有权转移给完成回调的异步调用级生命周期。
     */
    public RpcAsyncHookInvocation openAsync(RpcCallContext context) {
        return new RpcAsyncHookInvocation(context, hooks);
    }

    /**
     * @deprecated 请改用 {@link #open(RpcCallContext)} 获取调用级 Invocation。
     */
    @Deprecated(forRemoval = false)
    public void before(RpcCallContext context) {
        if (log.isDebugEnabled() && !hooks.isEmpty()) {
            log.debug("Executing before hooks for service={}, method={}, hooks={}",
                    context.getMetadata().getService(),
                    context.getMetadata().getMethod(),
                    hooks.size());
        }
        for (RpcHook hook : hooks) {
            hook.before(context);
        }
    }

    /**
     * @deprecated 请改用 {@link RpcHookInvocation#completeSuccess(RpcCallResult)}。
     */
    @Deprecated(forRemoval = false)
    public void after(RpcCallContext context, RpcCallResult result) {
        if (log.isDebugEnabled() && !hooks.isEmpty()) {
            log.debug("Executing after hooks for service={}, method={}, duration={}ms, hooks={}",
                    context.getMetadata().getService(),
                    context.getMetadata().getMethod(),
                    result.getDuration().toMillis(),
                    hooks.size());
        }
        for (RpcHook hook : hooks) {
            hook.after(context, result);
        }
    }

    /**
     * @deprecated 请改用 {@link RpcHookInvocation#completeFailure(RpcCallResult, Throwable)}。
     */
    @Deprecated(forRemoval = false)
    public void onError(RpcCallContext context, RpcCallResult result) {
        if (log.isDebugEnabled() && !hooks.isEmpty()) {
            log.debug("Executing onError hooks for service={}, method={}, duration={}ms, error={}, hooks={}",
                    context.getMetadata().getService(),
                    context.getMetadata().getMethod(),
                    result.getDuration().toMillis(),
                    result.getError().map(e -> e.getClass().getSimpleName()).orElse("null"),
                    hooks.size());
        }
        for (RpcHook hook : hooks) {
            hook.onError(context, result);
        }
    }

    /**
     * @deprecated 请改用 {@link RpcHookInvocation#close()}。
     */
    @Deprecated(forRemoval = false)
    public void cleanup(RpcCallContext context) {
        if (log.isDebugEnabled() && !hooks.isEmpty()) {
            log.debug("Executing cleanup hooks for service={}, method={}, hooks={}",
                    context.getMetadata().getService(),
                    context.getMetadata().getMethod(),
                    hooks.size());
        }
        for (RpcHook hook : hooks) {
            hook.cleanup(context);
        }
    }
}
