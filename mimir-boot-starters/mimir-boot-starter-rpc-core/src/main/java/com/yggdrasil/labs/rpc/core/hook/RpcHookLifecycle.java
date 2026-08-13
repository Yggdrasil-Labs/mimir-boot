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
 * 单次 RPC 调用的内部 Hook 生命周期状态机。
 */
final class RpcHookLifecycle {

    private static final Logger log = LoggerFactory.getLogger(RpcHookInvocation.class);

    private final RpcCallContext context;
    private final List<RpcHook> hooks;
    private final List<RpcHook> entered = Collections.synchronizedList(new ArrayList<>());
    private final Object lifecycleMonitor = new Object();
    private final AtomicReference<State> state = new AtomicReference<>(State.NEW);
    private Thread beforeThread;

    RpcHookLifecycle(RpcCallContext context, List<RpcHook> hooks) {
        this.context = context;
        this.hooks = hooks;
    }

    void before() {
        synchronized (lifecycleMonitor) {
            if (state.get() != State.NEW) {
                throw new IllegalStateException("RPC Hook invocation before phase is already running or completed");
            }
            state.set(State.BEFORE_RUNNING);
            beforeThread = Thread.currentThread();
        }
        try {
            for (RpcHook hook : hooks) {
                entered.add(hook);
                hook.before(context);
            }
        } finally {
            synchronized (lifecycleMonitor) {
                beforeThread = null;
                if (state.get() == State.BEFORE_RUNNING) {
                    state.set(State.READY);
                }
                lifecycleMonitor.notifyAll();
            }
        }
    }

    void completeSuccess(RpcCallResult result) {
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

    void completeFailure(RpcCallResult result, Throwable primaryError) {
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

    void completeWithoutResult() {
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

    private boolean claimCompletion() {
        synchronized (lifecycleMonitor) {
            waitForBeforeToFinish();
            if (state.get() == State.NEW) {
                state.set(State.COMPLETING);
                return true;
            }
            if (state.get() != State.READY) {
                return false;
            }
            state.set(State.COMPLETING);
            return true;
        }
    }

    private void waitForBeforeToFinish() {
        synchronized (lifecycleMonitor) {
            boolean interrupted = false;
            try {
                while (state.get() == State.BEFORE_RUNNING) {
                    if (beforeThread == Thread.currentThread()) {
                        throw new IllegalStateException("RPC Hook invocation cannot complete from its before phase");
                    }
                    try {
                        lifecycleMonitor.wait();
                    } catch (InterruptedException exception) {
                        interrupted = true;
                    }
                }
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
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
        NEW,
        BEFORE_RUNNING,
        READY,
        COMPLETING,
        CLOSED
    }
}
