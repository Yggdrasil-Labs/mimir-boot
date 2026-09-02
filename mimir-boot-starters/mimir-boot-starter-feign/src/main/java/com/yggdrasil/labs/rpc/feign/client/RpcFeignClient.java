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
import java.util.LinkedHashMap;
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
    /**
     * 仅允许进入 Hook 元数据的请求头，避免任意扩展凭据头经 Hook 或日志泄露。
     */
    private static final Set<String> SAFE_ATTACHMENT_HEADERS = Set.of(
            "accept",
            "accept-encoding",
            "accept-language",
            "content-type",
            "user-agent",
            "x-request-id",
            "x-correlation-id",
            "x-trace-id",
            "traceparent",
            "tracestate",
            "b3",
            "x-b3-traceid",
            "x-b3-spanid",
            "x-b3-parentspanid",
            "x-b3-sampled",
            "x-b3-flags");

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
        SanitizedUrl sanitizedUrl = sanitizeUrl(request.url());
        if (!properties.isEnabled()) {
            if (log.isDebugEnabled()) {
                log.debug("RpcFeignClient: Filter disabled, bypassing for url={}", sanitizedUrl.debugUrl());
            }
            return delegate.execute(request, options);
        }

        RpcCallMetadata metadata = RpcCallMetadata.builder()
                .service(sanitizedUrl.service())
                .method(request.httpMethod().name())
                .protocol(protocol(sanitizedUrl))
                .target(sanitizedUrl.target())
                .attachments(toStringMap(request.headers()))
                .build();
        RpcCallContext context = RpcCallContext.create(metadata);

        if (log.isDebugEnabled()) {
            log.debug("RpcFeignClient: Processing HTTP call - service={}, method={}, protocol={}, target={}, url={}",
                    metadata.getService(),
                    metadata.getMethod(),
                    metadata.getProtocol(),
                    metadata.getTarget(),
                    sanitizedUrl.debugUrl());
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
                        sanitizedUrl.debugUrl(),
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
                        sanitizedUrl.debugUrl(),
                        duration.toMillis(),
                        throwable.getClass().getSimpleName());
            }
            invocation.completeFailure(RpcCallResult.failure(duration, throwable), throwable);
            throw propagate(throwable);
        } finally {
            invocation.close();
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
        if (request.url() == null) {
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
        Map<String, String> map = new LinkedHashMap<>();
        headers.forEach((k, v) -> {
            if (isSafeAttachmentHeader(k) && v != null && !v.isEmpty()) {
                map.put(k, String.join(",", v));
            }
        });
        return map;
    }


    private SanitizedUrl sanitizeUrl(String rawUrl) {
        if (rawUrl == null) {
            return placeholder("[missing-url]");
        }
        try {
            URI uri = URI.create(rawUrl);
            if (uri.isOpaque()) {
                return placeholder("[opaque-url]");
            }
            if (uri.isAbsolute()) {
                if (uri.getHost() == null) {
                    return placeholder("[invalid-authority]");
                }
                String authority = authority(uri);
                String path = uri.getRawPath() == null ? "" : uri.getRawPath();
                return new SanitizedUrl(uri.getHost(), authority, uri.getScheme() + "://" + authority + path);
            }
            String path = uri.getRawPath() == null ? "" : uri.getRawPath();
            return new SanitizedUrl("[unknown-service]", path, path);
        } catch (IllegalArgumentException exception) {
            return placeholder("[invalid-url]");
        }
    }

    private SanitizedUrl placeholder(String value) {
        return new SanitizedUrl("[unknown-service]", value, value);
    }

    private String authority(URI uri) {
        String host = uri.getHost();
        if (host.indexOf(':') >= 0 && !host.startsWith("[")) {
            host = "[" + host + "]";
        }
        return uri.getPort() < 0 ? host : host + ":" + uri.getPort();
    }

    private String protocol(SanitizedUrl sanitizedUrl) {
        String debugUrl = sanitizedUrl.debugUrl();
        if (debugUrl.startsWith("[")) {
            return null;
        }
        return URI.create(debugUrl).getScheme();
    }

    private record SanitizedUrl(String service, String target, String debugUrl) {}
    private static boolean isSafeAttachmentHeader(String name) {
        return name != null && SAFE_ATTACHMENT_HEADERS.contains(name.toLowerCase(Locale.ROOT));
    }
}
