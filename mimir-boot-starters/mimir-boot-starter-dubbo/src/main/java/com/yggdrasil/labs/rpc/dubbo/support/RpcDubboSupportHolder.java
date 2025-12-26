package com.yggdrasil.labs.rpc.dubbo.support;

import com.yggdrasil.labs.rpc.core.hook.RpcHookChain;
import com.yggdrasil.labs.rpc.core.tracing.RpcTracerBridge;
import com.yggdrasil.labs.rpc.dubbo.config.DubboProperties;

/**
 * 为 Dubbo Filter 提供 Spring 管理的依赖（静态持有，Dubbo SPI 创建 Filter 时可获取）。
 */
public final class RpcDubboSupportHolder {

    private static RpcHookChain hookChain;
    private static RpcTracerBridge tracerBridge;
    private static DubboProperties properties;
    private static final RpcDubboSupportHolder INSTANCE = new RpcDubboSupportHolder();

    private RpcDubboSupportHolder() {}

    public static RpcDubboSupportHolder getInstance() {
        return INSTANCE;
    }

    public static void set(RpcHookChain hookChain, RpcTracerBridge tracerBridge, DubboProperties properties) {
        RpcDubboSupportHolder.hookChain = hookChain;
        RpcDubboSupportHolder.tracerBridge = tracerBridge;
        RpcDubboSupportHolder.properties = properties;
    }

    public RpcHookChain getHookChain() {
        return hookChain;
    }

    public RpcTracerBridge getTracerBridge() {
        return tracerBridge;
    }

    public DubboProperties getProperties() {
        return properties;
    }
}

