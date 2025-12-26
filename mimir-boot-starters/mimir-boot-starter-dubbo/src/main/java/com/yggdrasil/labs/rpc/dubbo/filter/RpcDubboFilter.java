package com.yggdrasil.labs.rpc.dubbo.filter;

import com.yggdrasil.labs.rpc.core.context.RpcCallContext;
import com.yggdrasil.labs.rpc.core.context.RpcCallMetadata;
import com.yggdrasil.labs.rpc.core.context.RpcCallResult;
import com.yggdrasil.labs.rpc.core.hook.RpcHookChain;
import com.yggdrasil.labs.rpc.core.tracing.RpcTracerBridge;
import com.yggdrasil.labs.rpc.dubbo.config.DubboProperties;
import com.yggdrasil.labs.rpc.dubbo.support.RpcDubboSupportHolder;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Activate(group = {CommonConstants.CONSUMER, CommonConstants.PROVIDER})
public class RpcDubboFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RpcDubboFilter.class);

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        RpcDubboSupportHolder holder = RpcDubboSupportHolder.getInstance();
        DubboProperties properties = holder.getProperties();
        RpcHookChain hookChain = holder.getHookChain();
        RpcTracerBridge tracerBridge = holder.getTracerBridge();

        if (properties == null || hookChain == null || tracerBridge == null) {
            // Spring 未初始化，降级为直通
            if (log.isDebugEnabled()) {
                log.debug("RpcDubboFilter: Spring not initialized, bypassing filter for service={}, method={}",
                        invoker.getInterface().getName(),
                        invocation.getMethodName());
            }
            return invoker.invoke(invocation);
        }
        if (!properties.isEnabled()) {
            if (log.isDebugEnabled()) {
                log.debug("RpcDubboFilter: Filter disabled, bypassing for service={}, method={}",
                        invoker.getInterface().getName(),
                        invocation.getMethodName());
            }
            return invoker.invoke(invocation);
        }

        Map<String, String> attachments = invocation.getObjectAttachments() == null
                ? null
                : invocation.getObjectAttachments().entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                Map.Entry::getKey,
                                e -> e.getValue() == null ? null : String.valueOf(e.getValue())));

        RpcCallMetadata metadata = RpcCallMetadata.builder()
                .service(invoker.getInterface().getName())
                .method(invocation.getMethodName())
                .protocol(invoker.getUrl().getProtocol())
                .target(invoker.getUrl().getAddress())
                .attachments(attachments)
                .build();
        RpcCallContext context = RpcCallContext.create(metadata);

        if (log.isDebugEnabled()) {
            log.debug("RpcDubboFilter: Processing RPC call - service={}, method={}, protocol={}, target={}",
                    metadata.getService(),
                    metadata.getMethod(),
                    metadata.getProtocol(),
                    metadata.getTarget());
        }

        Instant start = Instant.now();
        hookChain.before(context);

        if (properties.isContextPropagationEnabled()) {
            Map<String, String> injected = tracerBridge.inject(context);
            if (injected != null && !injected.isEmpty()) {
                if (log.isDebugEnabled()) {
                    log.debug("RpcDubboFilter: Injecting context propagation headers: {}", injected.keySet());
                }
                injected.forEach(invocation::setAttachment);
            }
        }

        try {
            Result result = invoker.invoke(invocation);
            Duration duration = Duration.between(start, Instant.now());
            if (log.isDebugEnabled()) {
                log.debug("RpcDubboFilter: RPC call succeeded - service={}, method={}, duration={}ms",
                        metadata.getService(),
                        metadata.getMethod(),
                        duration.toMillis());
            }
            hookChain.after(context, RpcCallResult.success(duration));
            return result;
        } catch (RuntimeException ex) {
            Duration duration = Duration.between(start, Instant.now());
            if (log.isDebugEnabled()) {
                log.debug("RpcDubboFilter: RPC call failed - service={}, method={}, duration={}ms, error={}",
                        metadata.getService(),
                        metadata.getMethod(),
                        duration.toMillis(),
                        ex.getClass().getSimpleName(),
                        ex);
            }
            hookChain.onError(context, RpcCallResult.failure(duration, ex));
            throw ex;
        } finally {
            hookChain.cleanup(context);
        }
    }
}

