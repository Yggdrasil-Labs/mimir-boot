package com.yggdrasil.labs.rpc.dubbo.support;

import java.util.concurrent.atomic.AtomicReference;

import com.yggdrasil.labs.rpc.core.hook.RpcHookChain;
import com.yggdrasil.labs.rpc.core.tracing.RpcTracerBridge;
import com.yggdrasil.labs.rpc.dubbo.config.DubboProperties;

/**
 * 为 Dubbo Filter 提供 Spring 管理的依赖（静态持有，Dubbo SPI 创建 Filter 时可获取）。
 *
 * <p>每个 Spring ApplicationContext 初始化时发布一个完整快照；同一进程内多个上下文会共享最后一次发布的快照。
 */
public final class RpcDubboSupportHolder {

    private static final AtomicReference<Snapshot> SNAPSHOT =
            new AtomicReference<>(new Snapshot(null, null, null));
    private static final RpcDubboSupportHolder INSTANCE = new RpcDubboSupportHolder();

    private RpcDubboSupportHolder() {}

    /** 返回供 Spring 装配的兼容持有器实例。 */
    public static RpcDubboSupportHolder getInstance() {
        return INSTANCE;
    }

    /** 原子发布同一应用上下文中的完整 RPC 依赖快照。 */
    public static void set(
            RpcHookChain hookChain, RpcTracerBridge tracerBridge, DubboProperties properties) {
        SNAPSHOT.set(new Snapshot(hookChain, tracerBridge, properties));
    }

    /** 返回当前发布的依赖快照。 */
    public static Snapshot current() {
        return SNAPSHOT.get();
    }

    /** 返回当前快照中的 Hook 链。 */
    public RpcHookChain getHookChain() {
        return current().hookChain();
    }

    /** 返回当前快照中的追踪桥。 */
    public RpcTracerBridge getTracerBridge() {
        return current().tracerBridge();
    }

    /** 返回当前快照中的 Dubbo 配置。 */
    public DubboProperties getProperties() {
        return current().properties();
    }

    public record Snapshot(
            RpcHookChain hookChain, RpcTracerBridge tracerBridge, DubboProperties properties) {}
}
