package com.yggdrasil.labs.rpc.core.context;

import java.time.Duration;
import java.util.Optional;

/** RPC 调用结果信息，用于 Hook 回调。 */
public final class RpcCallResult {

    private final boolean success;
    private final Duration duration;
    private final Throwable error;

    private RpcCallResult(boolean success, Duration duration, Throwable error) {
        this.success = success;
        this.duration = duration;
        this.error = error;
    }

    /** 创建记录耗时的成功结果。 */
    public static RpcCallResult success(Duration duration) {
        return new RpcCallResult(true, duration, null);
    }

    /** 创建同时保留耗时和原始异常的失败结果。 */
    public static RpcCallResult failure(Duration duration, Throwable error) {
        return new RpcCallResult(false, duration, error);
    }

    public boolean isSuccess() {
        return success;
    }

    public Duration getDuration() {
        return duration;
    }

    public Optional<Throwable> getError() {
        return Optional.ofNullable(error);
    }
}
