package com.yggdrasil.labs.rpc.core.hook;

import com.yggdrasil.labs.rpc.core.context.RpcCallContext;
import com.yggdrasil.labs.rpc.core.context.RpcCallResult;
import java.util.List;

/**
 * 异步 RPC 调用的 Hook 生命周期。
 *
 * <p>该句柄不实现 {@link AutoCloseable}，因为生命周期所有权可转移给异步完成回调。调用方必须在成功、失败或未完成异步注册时调用一个终态方法；
 * 重复调用终态方法是安全的，清理只会执行一次。</p>
 */
public final class RpcAsyncHookInvocation {

    private final RpcHookLifecycle lifecycle;

    RpcAsyncHookInvocation(RpcCallContext context, List<RpcHook> hooks) {
        this.lifecycle = new RpcHookLifecycle(context, hooks);
    }

    /**
     * 依序执行前置阶段。
     */
    public void before() {
        lifecycle.before();
    }

    /**
     * 完成成功调用并执行后置阶段和清理。
     */
    public void completeSuccess(RpcCallResult result) {
        lifecycle.completeSuccess(result);
    }

    /**
     * 完成失败调用，并把后置及清理异常附加到业务主异常。
     */
    public void completeFailure(RpcCallResult result, Throwable primaryError) {
        lifecycle.completeFailure(result, primaryError);
    }

    /**
     * 未产生调用结果时的兜底终态，只执行清理。
     */
    public void completeWithoutResult() {
        lifecycle.completeWithoutResult();
    }

    boolean isClosed() {
        return lifecycle.isClosed();
    }
}
