package com.yggdrasil.labs.rpc.dubbo.filter;

import com.yggdrasil.labs.rpc.core.context.RpcCallContext;
import com.yggdrasil.labs.rpc.core.context.RpcCallMetadata;
import com.yggdrasil.labs.rpc.core.context.RpcCallResult;
import com.yggdrasil.labs.rpc.core.hook.RpcAsyncHookInvocation;
import com.yggdrasil.labs.rpc.core.hook.RpcHookChain;
import com.yggdrasil.labs.rpc.core.tracing.RpcTracerBridge;
import com.yggdrasil.labs.rpc.core.tracing.RpcTraceScope;
import com.yggdrasil.labs.rpc.dubbo.config.DubboProperties;
import com.yggdrasil.labs.rpc.dubbo.support.RpcDubboSupportHolder;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
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
        RpcDubboSupportHolder.Snapshot support = RpcDubboSupportHolder.current();
        DubboProperties properties = support.properties();
        RpcHookChain hookChain = support.hookChain();
        RpcTracerBridge tracerBridge = support.tracerBridge();

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

        Map<String, String> attachments = copyAttachments(invocation.getObjectAttachments());

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
        RpcTraceScope traceScope = RpcTraceScope.noop();
        Throwable primaryFailure = null;
        boolean providerSide = CommonConstants.PROVIDER_SIDE.equals(
                invoker.getUrl().getParameter(CommonConstants.SIDE_KEY));

        try {
            RpcAsyncHookInvocation hookInvocation = hookChain.openAsync(context);
            boolean asyncInvocation = false;
            try {
                if (properties.isContextPropagationEnabled() && providerSide) {
                    RpcTraceScope extractedScope = tracerBridge.extractScope(
                            context, attachments == null ? Map.of() : attachments);
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
                if (result instanceof AsyncRpcResult) {
                    closeScope(traceScope, null);
                    traceScope = RpcTraceScope.noop();
                    result.whenCompleteWithContext((completedResult, throwable) -> {
                        RpcTraceScope completionScope = RpcTraceScope.noop();
                        try {
                            if (properties.isContextPropagationEnabled() && providerSide) {
                                RpcTraceScope extractedScope = tracerBridge.extractScope(
                                        context, attachments == null ? Map.of() : attachments);
                                completionScope = extractedScope == null ? RpcTraceScope.noop() : extractedScope;
                            }
                            completeCall(hookInvocation, metadata, start, completedResult, throwable);
                        } finally {
                            closeScope(completionScope, resolveFailure(completedResult, throwable));
                            hookInvocation.completeWithoutResult();
                        }
                    });
                    asyncInvocation = true;
                } else {
                    primaryFailure = resolveFailure(result, null);
                    completeCall(hookInvocation, metadata, start, result, null);
                }
                return result;
            } catch (Throwable throwable) {
                primaryFailure = throwable;
                completeCall(hookInvocation, metadata, start, null, throwable);
                throw propagate(throwable);
            } finally {
                if (!asyncInvocation) {
                    hookInvocation.completeWithoutResult();
                }
            }
        } finally {
            closeScope(traceScope, primaryFailure);
        }
    }

    private void completeCall(
            RpcAsyncHookInvocation hookInvocation,
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

    private Throwable resolveFailure(Result result, Throwable throwable) {
        if (throwable != null) {
            return throwable;
        }
        return result != null && result.hasException() ? result.getException() : null;
    }

    private void closeScope(RpcTraceScope scope, Throwable primaryFailure) {
        try {
            scope.close();
        } catch (Throwable closeFailure) {
            if (primaryFailure != null && closeFailure != primaryFailure) {
                primaryFailure.addSuppressed(closeFailure);
            }
            log.warn("RPC trace scope close failed", closeFailure);
        }
    }

    private Map<String, String> copyAttachments(Map<String, Object> source) {
        if (source == null) {
            return null;
        }
        Map<String, String> copied = new LinkedHashMap<>();
        source.forEach((key, value) -> copied.put(key, value == null ? null : String.valueOf(value)));
        return copied;
    }
}
