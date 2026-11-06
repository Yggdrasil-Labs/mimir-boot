package com.yggdrasil.labs.rpc.dubbo.filter;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.AsyncRpcResult;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yggdrasil.labs.rpc.core.context.RpcCallContext;
import com.yggdrasil.labs.rpc.core.context.RpcCallMetadata;
import com.yggdrasil.labs.rpc.core.context.RpcCallResult;
import com.yggdrasil.labs.rpc.core.hook.RpcAsyncHookInvocation;
import com.yggdrasil.labs.rpc.core.hook.RpcHookChain;
import com.yggdrasil.labs.rpc.core.tracing.RpcTraceScope;
import com.yggdrasil.labs.rpc.core.tracing.RpcTracerBridge;
import com.yggdrasil.labs.rpc.dubbo.config.DubboProperties;
import com.yggdrasil.labs.rpc.dubbo.support.RpcDubboSupportHolder;

/** 将 Dubbo 调用接入统一 Hook 生命周期与线程追踪作用域。 */
@Activate(group = {CommonConstants.CONSUMER, CommonConstants.PROVIDER})
public class RpcDubboFilter implements Filter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RpcDubboFilter.class);

    // 作用域由 closeScope 在同步返回和异步转交边界关闭；try-with-resources 会改变清理异常语义。
    @SuppressWarnings("PMD.CloseResource")
    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        RpcDubboSupportHolder.Snapshot support = RpcDubboSupportHolder.current();
        DubboProperties properties = support.properties();
        RpcHookChain hookChain = support.hookChain();
        RpcTracerBridge tracerBridge = support.tracerBridge();

        if (isUninitialized(properties, hookChain, tracerBridge)) {
            // Spring 未初始化，降级为直通
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                        "RpcDubboFilter: Spring not initialized, bypassing filter for service={},"
                                + " method={}",
                        invoker.getInterface().getName(),
                        invocation.getMethodName());
            }
            return invoker.invoke(invocation);
        }
        if (!properties.isEnabled()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                        "RpcDubboFilter: Filter disabled, bypassing for service={}, method={}",
                        invoker.getInterface().getName(),
                        invocation.getMethodName());
            }
            return invoker.invoke(invocation);
        }

        Map<String, String> attachments = copyAttachments(invocation.getObjectAttachments());

        RpcCallMetadata metadata =
                RpcCallMetadata.builder()
                        .service(invoker.getInterface().getName())
                        .method(invocation.getMethodName())
                        .protocol(invoker.getUrl().getProtocol())
                        .target(invoker.getUrl().getAddress())
                        .attachments(attachments)
                        .build();
        RpcCallContext context = RpcCallContext.create(metadata);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    "RpcDubboFilter: Processing RPC call - service={}, method={}, protocol={},"
                            + " target={}",
                    metadata.getService(),
                    metadata.getMethod(),
                    metadata.getProtocol(),
                    metadata.getTarget());
        }

        Instant start = Instant.now();
        RpcTraceScope traceScope = RpcTraceScope.noop();
        Throwable primaryFailure = null;
        boolean providerSide =
                CommonConstants.PROVIDER_SIDE.equals(
                        invoker.getUrl().getParameter(CommonConstants.SIDE_KEY));

        try {
            RpcAsyncHookInvocation hookInvocation = hookChain.openAsync(context);
            boolean asyncInvocation = false;
            try {
                if (properties.isContextPropagationEnabled() && providerSide) {
                    RpcTraceScope extractedScope =
                            tracerBridge.extractScope(
                                    context, attachments == null ? Map.of() : attachments);
                    traceScope = extractedScope == null ? RpcTraceScope.noop() : extractedScope;
                }
                hookInvocation.before();

                injectContext(properties, providerSide, tracerBridge, context, invocation);

                Result result = invoker.invoke(invocation);
                if (result instanceof AsyncRpcResult) {
                    closeScope(traceScope, null);
                    traceScope = RpcTraceScope.noop();
                    registerAsyncCompletion(
                            result,
                            hookInvocation,
                            metadata,
                            start,
                            properties,
                            providerSide,
                            tracerBridge,
                            context,
                            attachments);
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

    private boolean isUninitialized(
            DubboProperties properties, RpcHookChain hookChain, RpcTracerBridge tracerBridge) {
        return properties == null || hookChain == null || tracerBridge == null;
    }

    private void injectContext(
            DubboProperties properties,
            boolean providerSide,
            RpcTracerBridge tracerBridge,
            RpcCallContext context,
            Invocation invocation) {
        if (properties.isContextPropagationEnabled() && !providerSide) {
            Map<String, String> injected = tracerBridge.inject(context);
            if (injected != null && !injected.isEmpty()) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(
                            "RpcDubboFilter: Injecting context propagation headers: {}",
                            injected.keySet());
                }
                injected.forEach(invocation::setAttachment);
            }
        }
    }

    private void registerAsyncCompletion(
            Result result,
            RpcAsyncHookInvocation hookInvocation,
            RpcCallMetadata metadata,
            Instant start,
            DubboProperties properties,
            boolean providerSide,
            RpcTracerBridge tracerBridge,
            RpcCallContext context,
            Map<String, String> attachments) {
        result.whenCompleteWithContext(
                (completedResult, throwable) -> {
                    RpcTraceScope completionScope = RpcTraceScope.noop();
                    try {
                        if (properties.isContextPropagationEnabled() && providerSide) {
                            completionScope =
                                    extractCompletionScope(tracerBridge, context, attachments);
                        }
                        completeCall(hookInvocation, metadata, start, completedResult, throwable);
                    } finally {
                        closeScope(completionScope, resolveFailure(completedResult, throwable));
                        hookInvocation.completeWithoutResult();
                    }
                });
    }

    private void completeCall(
            RpcAsyncHookInvocation hookInvocation,
            RpcCallMetadata metadata,
            Instant start,
            Result result,
            Throwable throwable) {
        Duration duration = Duration.between(start, Instant.now());
        Throwable error =
                throwable != null
                        ? throwable
                        : result != null && result.hasException() ? result.getException() : null;
        if (error != null) {
            logFailure(metadata, duration, error);
            hookInvocation.completeFailure(RpcCallResult.failure(duration, error), error);
        } else {
            logSuccess(metadata, duration);
            hookInvocation.completeSuccess(RpcCallResult.success(duration));
        }
    }

    @SuppressWarnings("java:S1181") // 完成阶段追踪是辅助能力，失败时不得覆盖已完成的 RPC 结果
    private RpcTraceScope extractCompletionScope(
            RpcTracerBridge tracerBridge, RpcCallContext context, Map<String, String> attachments) {
        try {
            RpcTraceScope extractedScope =
                    tracerBridge.extractScope(
                            context, attachments == null ? Map.of() : attachments);
            return extractedScope == null ? RpcTraceScope.noop() : extractedScope;
        } catch (Throwable extractionFailure) {
            LOGGER.warn(
                    "RPC trace scope extraction failed during async completion; preserving RPC"
                            + " result, error={}",
                    extractionFailure.getClass().getName());
            return RpcTraceScope.noop();
        }
    }

    private void logSuccess(RpcCallMetadata metadata, Duration duration) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    "RpcDubboFilter: RPC call succeeded - service={}, method={}, duration={}ms",
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
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    "RpcDubboFilter: RPC call failed - service={}, method={}, duration={}ms,"
                            + " error={}",
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

    // trace 清理必须吞掉所有 Throwable；按身份排除异常自身，避免 addSuppressed 自抑制。
    @SuppressWarnings({"java:S1181", "PMD.CompareObjectsWithEquals"})
    private void closeScope(RpcTraceScope scope, Throwable primaryFailure) {
        try {
            scope.close();
        } catch (Throwable closeFailure) {
            if (primaryFailure != null && closeFailure != primaryFailure) {
                primaryFailure.addSuppressed(closeFailure);
            }
            LOGGER.warn("RPC trace scope close failed", closeFailure);
        }
    }

    private Map<String, String> copyAttachments(Map<String, Object> source) {
        if (source == null) {
            return Map.of();
        }
        Map<String, String> copied = new LinkedHashMap<>();
        source.forEach(
                (key, value) -> copied.put(key, value == null ? null : String.valueOf(value)));
        return copied;
    }
}
