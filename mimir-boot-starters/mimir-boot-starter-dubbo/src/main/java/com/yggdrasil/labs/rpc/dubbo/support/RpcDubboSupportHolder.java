package com.yggdrasil.labs.rpc.dubbo.support;

import com.yggdrasil.labs.rpc.core.hook.RpcHookChain;
import com.yggdrasil.labs.rpc.core.tracing.RpcTracerBridge;
import com.yggdrasil.labs.rpc.dubbo.config.DubboProperties;

/**
 * 为 Dubbo Filter 提供 Spring 管理的依赖（静态持有，Dubbo SPI 创建 Filter 时可获取）。
 *
 * <p>每个 Spring ApplicationContext 初始化时发布一个完整快照；同一进程内多个上下文会共享最后一次发布的快照。</p>
 */
public final class RpcDubboSupportHolder {

    private static volatile Snapshot snapshot = new Snapshot(null, null, null);
    private static final RpcDubboSupportHolder INSTANCE = new RpcDubboSupportHolder();

    private RpcDubboSupportHolder() {}

    public static RpcDubboSupportHolder getInstance() {
        return INSTANCE;
    }

    public static void set(RpcHookChain hookChain, RpcTracerBridge tracerBridge, DubboProperties properties) {
        snapshot = new Snapshot(hookChain, tracerBridge, properties);
    }

    public static Snapshot current() {
        return snapshot;
    }

    public RpcHookChain getHookChain() {
        return current().hookChain();
    }

    public RpcTracerBridge getTracerBridge() {
        return current().tracerBridge();
    }

    public DubboProperties getProperties() {
        return current().properties();
    }

    public record Snapshot(RpcHookChain hookChain, RpcTracerBridge tracerBridge, DubboProperties properties) {}
}
