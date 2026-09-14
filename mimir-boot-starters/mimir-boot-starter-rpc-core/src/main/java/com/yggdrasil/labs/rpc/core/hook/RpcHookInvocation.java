package com.yggdrasil.labs.rpc.core.hook;

import java.util.List;

import com.yggdrasil.labs.rpc.core.context.RpcCallContext;
import com.yggdrasil.labs.rpc.core.context.RpcCallResult;

/**
 * 同步 RPC 调用的 Hook 生命周期。
 *
 * <p>该对象不能跨调用复用。每次终态竞争仅允许一个获胜者执行后置阶段和清理。同步调用方应使用 try-with-resources 或在 finally 中关闭。
 */
public final class RpcHookInvocation implements AutoCloseable {

    private final RpcHookLifecycle lifecycle;

    RpcHookInvocation(RpcCallContext context, List<RpcHook> hooks) {
        this.lifecycle = new RpcHookLifecycle(context, hooks);
    }

    /** 依序执行前置阶段。Hook 会在调用前登记，以便其前置阶段抛错时仍可获得清理。 */
    public void before() {
        lifecycle.before();
    }

    /** 完成成功调用；后置扩展失败只记录，不得改写业务结果。 */
    public void completeSuccess(RpcCallResult result) {
        lifecycle.completeSuccess(result);
    }

    /** 完成失败调用；后置及清理异常按发生顺序附加到业务主异常。 */
    public void completeFailure(RpcCallResult result, Throwable primaryError) {
        lifecycle.completeFailure(result, primaryError);
    }

    /** 无主异常的兜底关闭，只执行清理。 */
    @Override
    public void close() {
        lifecycle.completeWithoutResult();
    }

    boolean isClosed() {
        return lifecycle.isClosed();
    }
}
