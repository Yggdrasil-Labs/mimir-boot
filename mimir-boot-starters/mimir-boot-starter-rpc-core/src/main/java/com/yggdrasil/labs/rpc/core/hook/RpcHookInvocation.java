package com.yggdrasil.labs.rpc.core.hook;

import com.yggdrasil.labs.rpc.core.context.RpcCallContext;
import com.yggdrasil.labs.rpc.core.context.RpcCallResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 单次 RPC 调用的 Hook 生命周期。
 *
 * <p>该对象不能跨调用复用。每次终态竞争仅允许一个获胜者执行后置阶段和清理，避免异步结果与调用方兜底关闭重复执行。</p>
 */
public final class RpcHookInvocation implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RpcHookInvocation.class);

    private final RpcCallContext context;
    private final List<RpcHook> hooks;
    private final List<RpcHook> entered = Collections.synchronizedList(new ArrayList<>());
    private final AtomicReference<State> state = new AtomicReference<>(State.OPEN);

    RpcHookInvocation(RpcCallContext context, List<RpcHook> hooks) {
        this.context = context;
        this.hooks = hooks;
    }

    /**
     * 依序执行前置阶段。Hook 会在调用前登记，以便其前置阶段抛错时仍可获得清理。
     */
    public void before() {
        ensureOpen();
        for (RpcHook hook : hooks) {
            entered.add(hook);
            hook.before(context);
        }
    }

    /**
     * 完成成功调用；后置扩展失败只记录，不得改写业务结果。
     */
    public void completeSuccess(RpcCallResult result) {
        if (!claimCompletion()) {
            return;
        }
        try {
            for (RpcHook hook : enteredSnapshot()) {
                runBestEffort(() -> hook.after(context, result), "after");
            }
            cleanupBestEffort(null);
        } finally {
            state.set(State.CLOSED);
        }
    }

    /**
     * 完成失败调用；后置及清理异常按发生顺序附加到业务主异常。
     */
    public void completeFailure(RpcCallResult result, Throwable primaryError) {
        if (!claimCompletion()) {
            return;
        }
        try {
            for (RpcHook hook : enteredSnapshot()) {
                runWithSuppressed(primaryError, () -> hook.onError(context, result), "onError");
            }
            cleanupBestEffort(primaryError);
        } finally {
            state.set(State.CLOSED);
        }
    }

    /**
     * 无主异常的兜底关闭，只执行清理。
     */
    @Override
    public void close() {
        if (!claimCompletion()) {
            return;
        }
        try {
            cleanupBestEffort(null);
        } finally {
            state.set(State.CLOSED);
        }
    }

    boolean isClosed() {
        return state.get() == State.CLOSED;
    }

    private void ensureOpen() {
        if (state.get() != State.OPEN) {
            throw new IllegalStateException("RPC Hook invocation is already completing or closed");
        }
    }

    private boolean claimCompletion() {
        return state.compareAndSet(State.OPEN, State.COMPLETING);
    }

    private List<RpcHook> enteredSnapshot() {
        synchronized (entered) {
            return List.copyOf(entered);
        }
    }

    private void cleanupBestEffort(Throwable primaryError) {
        List<RpcHook> completed = enteredSnapshot();
        for (int index = completed.size() - 1; index >= 0; index--) {
            RpcHook hook = completed.get(index);
            if (primaryError == null) {
                runBestEffort(() -> hook.cleanup(context), "cleanup");
            } else {
                runWithSuppressed(primaryError, () -> hook.cleanup(context), "cleanup");
            }
        }
    }

    private void runBestEffort(ThrowingRunnable action, String phase) {
        try {
            action.run();
        } catch (Throwable failure) {
            log.warn("RPC Hook {} phase failed and was ignored", phase, failure);
        }
    }

    private void runWithSuppressed(Throwable primaryError, ThrowingRunnable action, String phase) {
        try {
            action.run();
        } catch (Throwable failure) {
            if (failure != primaryError) {
                primaryError.addSuppressed(failure);
            }
            log.warn("RPC Hook {} phase failed after business error", phase, failure);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Throwable;
    }

    private enum State {
        OPEN,
        COMPLETING,
        CLOSED
    }
}
