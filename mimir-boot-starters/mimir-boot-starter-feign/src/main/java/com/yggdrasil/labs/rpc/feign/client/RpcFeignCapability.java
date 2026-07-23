package com.yggdrasil.labs.rpc.feign.client;

import com.yggdrasil.labs.rpc.core.hook.RpcHookChain;
import com.yggdrasil.labs.rpc.core.tracing.RpcTracerBridge;
import com.yggdrasil.labs.rpc.feign.config.FeignProperties;
import feign.Capability;
import feign.Client;

/**
 * 为 Feign 最终选定的 HTTP 客户端附加 RPC 调用能力。
 */
public class RpcFeignCapability implements Capability {

    private final RpcHookChain hookChain;
    private final RpcTracerBridge tracerBridge;
    private final FeignProperties properties;

    public RpcFeignCapability(RpcHookChain hookChain, RpcTracerBridge tracerBridge, FeignProperties properties) {
        this.hookChain = hookChain;
        this.tracerBridge = tracerBridge;
        this.properties = properties;
    }

    @Override
    public Client enrich(Client client) {
        if (client instanceof RpcFeignClient) {
            return client;
        }
        return new RpcFeignClient(client, hookChain, tracerBridge, properties);
    }
}
