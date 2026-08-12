package com.yggdrasil.labs.rpc.dubbo.filter;

import com.yggdrasil.labs.rpc.core.context.RpcCallContext;
import com.yggdrasil.labs.rpc.core.context.RpcCallMetadata;
import com.yggdrasil.labs.rpc.core.context.RpcCallResult;
import com.yggdrasil.labs.rpc.core.hook.RpcHookChain;
import com.yggdrasil.labs.rpc.core.hook.RpcHookInvocation;
import com.yggdrasil.labs.rpc.core.tracing.RpcTracerBridge;
import com.yggdrasil.labs.rpc.core.tracing.RpcTraceScope;
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
import org.apache.dubbo.rpc.AsyncRpcResult;
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
        RpcHookInvocation hookInvocation = hookChain.open(context);
        boolean asyncInvocation = false;
        RpcTraceScope traceScope = RpcTraceScope.noop();
        boolean providerSide = CommonConstants.PROVIDER_SIDE.equals(
                invoker.getUrl().getParameter(CommonConstants.SIDE_KEY));

        try {
            if (properties.isContextPropagationEnabled() && providerSide) {
                RpcTraceScope extractedScope = tracerBridge.extractScope(context, attachments == null ? Map.of() : attachments);
                traceScope = extractedScope == null ? RpcTraceScope.noop() : extractedScope;
            }
            hookInvocation.before();

            if (properties.isContextPropagationEnabled() && !providerSide) {
                Map<String, String> injected = tracerBridge.inject(context);
                if (injected != null && !injected.isEmpty()) {
                    if (log.isDebugEnabled()) {
                        log.debug("RpcDubboFilter: Injecting context propagation headers: {}", injected.keySet());
                    }
                    injected.forEach(invocation::setAttachment);
                }
            }

            Result result = invoker.invoke(invocation);
            traceScope.close();
            traceScope = RpcTraceScope.noop();
            if (result instanceof AsyncRpcResult) {
                result.whenCompleteWithContext((completedResult, throwable) ->
                        completeCall(hookInvocation, metadata, start, completedResult, throwable));
                asyncInvocation = true;
            } else {
                completeCall(hookInvocation, metadata, start, result, null);
            }
            return result;
        } catch (Throwable throwable) {
            completeCall(hookInvocation, metadata, start, null, throwable);
            throw propagate(throwable);
        } finally {
            traceScope.close();
            if (!asyncInvocation) {
                hookInvocation.close();
            }
        }
    }

    private void completeCall(
            RpcHookInvocation hookInvocation,
            RpcCallMetadata metadata,
            Instant start,
        Result result,
        Throwable throwable) {
        Duration duration = Duration.between(start, Instant.now());
        Throwable error = throwable != null ? throwable : result != null && result.hasException()
                ? result.getException()
                : null;
        if (error != null) {
            logFailure(metadata, duration, error);
            hookInvocation.completeFailure(RpcCallResult.failure(duration, error), error);
        } else {
            logSuccess(metadata, duration);
            hookInvocation.completeSuccess(RpcCallResult.success(duration));
        }
    }

    private void logSuccess(RpcCallMetadata metadata, Duration duration) {
        if (log.isDebugEnabled()) {
            log.debug("RpcDubboFilter: RPC call succeeded - service={}, method={}, duration={}ms",
                    metadata.getService(),
                    metadata.getMethod(),
                    duration.toMillis());
        }
    }

    private RpcException propagate(Throwable throwable) {
        if (throwable instanceof Error error) {
            throw error;
        }
        if (throwable instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        return new RpcException(throwable);
    }

    private void logFailure(RpcCallMetadata metadata, Duration duration, Throwable error) {
        if (log.isDebugEnabled()) {
            log.debug("RpcDubboFilter: RPC call failed - service={}, method={}, duration={}ms, error={}",
                    metadata.getService(),
                    metadata.getMethod(),
                    duration.toMillis(),
                    error.getClass().getSimpleName(),
                    error);
        }
    }
}
