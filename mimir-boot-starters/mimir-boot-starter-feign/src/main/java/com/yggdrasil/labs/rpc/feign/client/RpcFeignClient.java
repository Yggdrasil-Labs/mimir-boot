package com.yggdrasil.labs.rpc.feign.client;

import com.yggdrasil.labs.rpc.core.context.RpcCallContext;
import com.yggdrasil.labs.rpc.core.context.RpcCallMetadata;
import com.yggdrasil.labs.rpc.core.context.RpcCallResult;
import com.yggdrasil.labs.rpc.core.hook.RpcHookChain;
import com.yggdrasil.labs.rpc.core.hook.RpcHookInvocation;
import com.yggdrasil.labs.rpc.core.tracing.RpcTracerBridge;
import com.yggdrasil.labs.rpc.feign.config.FeignProperties;
import feign.Client;
import feign.Request;
import feign.Response;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 包装 Feign Client，统一 Hook 与上下文传播。
 */
public class RpcFeignClient implements Client {

    private static final Logger log = LoggerFactory.getLogger(RpcFeignClient.class);
    private static final Set<String> SENSITIVE_ATTACHMENT_HEADERS = Set.of("authorization", "cookie");

    private final Client delegate;
    private final RpcHookChain hookChain;
    private final RpcTracerBridge tracerBridge;
    private final FeignProperties properties;

    public RpcFeignClient(Client delegate, RpcHookChain hookChain, RpcTracerBridge tracerBridge, FeignProperties properties) {
        this.delegate = delegate;
        this.hookChain = hookChain;
        this.tracerBridge = tracerBridge;
        this.properties = properties;
        log.debug("RpcFeignClient initialized with enabled={}, contextPropagationEnabled={}",
                properties.isEnabled(),
                properties.isContextPropagationEnabled());
    }

    @Override
    public Response execute(Request request, Request.Options options) throws IOException {
        if (!properties.isEnabled()) {
            if (log.isDebugEnabled()) {
                log.debug("RpcFeignClient: Filter disabled, bypassing for url={}", request.url());
            }
            return delegate.execute(request, options);
        }

        URI uri = URI.create(request.url());
        RpcCallMetadata metadata = RpcCallMetadata.builder()
                .service(uri.getHost())
                .method(request.httpMethod().name())
                .protocol(uri.getScheme())
                .target(uri.getAuthority())
                .attachments(toStringMap(request.headers()))
                .build();
        RpcCallContext context = RpcCallContext.create(metadata);

        if (log.isDebugEnabled()) {
            log.debug("RpcFeignClient: Processing HTTP call - service={}, method={}, protocol={}, target={}, url={}",
                    metadata.getService(),
                    metadata.getMethod(),
                    metadata.getProtocol(),
                    metadata.getTarget(),
                    request.url());
        }

        Instant start = Instant.now();
        RpcHookInvocation invocation = hookChain.open(context);

        try {
            invocation.before();
            Request wrapped = injectContext(request, context);
            Response response = delegate.execute(wrapped, options);
            Duration duration = Duration.between(start, Instant.now());
            if (log.isDebugEnabled()) {
                log.debug("RpcFeignClient: HTTP call succeeded - service={}, method={}, url={}, status={}, duration={}ms",
                        metadata.getService(),
                        metadata.getMethod(),
                        request.url(),
                        response.status(),
                        duration.toMillis());
            }
            invocation.completeSuccess(RpcCallResult.success(duration));
            return response;
        } catch (Throwable throwable) {
            Duration duration = Duration.between(start, Instant.now());
            if (log.isDebugEnabled()) {
                log.debug("RpcFeignClient: HTTP call failed - service={}, method={}, url={}, duration={}ms, error={}",
                        metadata.getService(),
                        metadata.getMethod(),
                        request.url(),
                        duration.toMillis(),
                        throwable.getClass().getSimpleName(),
                        throwable);
            }
            invocation.completeFailure(RpcCallResult.failure(duration, throwable), throwable);
            throw propagate(throwable);
        }
    }

    private Request injectContext(Request request, RpcCallContext context) {
        if (!properties.isContextPropagationEnabled()) {
            return request;
        }
        Map<String, String> injected = tracerBridge.inject(context);
        if (injected == null || injected.isEmpty()) {
            return request;
        }
        if (log.isDebugEnabled()) {
            log.debug("RpcFeignClient: Injecting context propagation headers: {}", injected.keySet());
        }
        Map<String, Collection<String>> newHeaders = new HashMap<>(request.headers());
        injected.forEach((key, value) -> newHeaders.put(key, java.util.List.of(value)));
        return Request.create(
                request.httpMethod(),
                request.url(),
                newHeaders,
                request.body(),
                request.charset(),
                request.requestTemplate());
    }

    private IOException propagate(Throwable throwable) throws IOException {
        if (throwable instanceof Error error) {
            throw error;
        }
        if (throwable instanceof IOException ioException) {
            return ioException;
        }
        if (throwable instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        return new IOException("RPC Feign call failed", throwable);
    }

    private Map<String, String> toStringMap(Map<String, Collection<String>> headers) {
        if (headers == null) {
            return null;
        }
        Map<String, String> map = new HashMap<>();
        headers.forEach((k, v) -> {
            if (!isSensitiveAttachmentHeader(k) && v != null && !v.isEmpty()) {
                map.put(k, v.iterator().next());
            }
        });
        return map;
    }

    private static boolean isSensitiveAttachmentHeader(String name) {
        return name != null && SENSITIVE_ATTACHMENT_HEADERS.contains(name.toLowerCase(Locale.ROOT));
    }
}
