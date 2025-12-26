package com.yggdrasil.labs.rpc.core.hook;

import com.yggdrasil.labs.rpc.core.context.RpcCallContext;
import com.yggdrasil.labs.rpc.core.context.RpcCallResult;
import org.springframework.core.Ordered;

/**
 * RPC Hook 扩展点：日志/观测/安全等能力可通过实现该接口插入。
 *
 * <p>默认提供空实现，便于可选覆盖。</p>
 */
public interface RpcHook extends Ordered {

    /**
     * 调用前置。
     */
    default void before(RpcCallContext context) {
        // no-op
    }

    /**
     * 调用成功后。
     */
    default void after(RpcCallContext context, RpcCallResult result) {
        // no-op
    }

    /**
     * 调用异常后。
     */
    default void onError(RpcCallContext context, RpcCallResult result) {
        // no-op
    }

    /**
     * 清理阶段（无论成功失败都会执行）。
     */
    default void cleanup(RpcCallContext context) {
        // no-op
    }

    @Override
    default int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}

