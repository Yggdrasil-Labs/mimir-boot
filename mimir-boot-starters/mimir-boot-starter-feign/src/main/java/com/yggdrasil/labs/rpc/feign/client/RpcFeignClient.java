package com.yggdrasil.labs.rpc.feign.client;

import com.yggdrasil.labs.rpc.core.context.RpcCallContext;
import com.yggdrasil.labs.rpc.core.context.RpcCallMetadata;
import com.yggdrasil.labs.rpc.core.context.RpcCallResult;
import com.yggdrasil.labs.rpc.core.hook.RpcHookChain;
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
        hookChain.before(context);

        Request wrapped = request;
        if (properties.isContextPropagationEnabled()) {
            Map<String, String> injected = tracerBridge.inject(context);
            if (injected != null && !injected.isEmpty()) {
                if (log.isDebugEnabled()) {
                    log.debug("RpcFeignClient: Injecting context propagation headers: {}", injected.keySet());
                }
                Map<String, Collection<String>> newHeaders = new HashMap<>(request.headers());
                injected.forEach((k, v) -> newHeaders.put(k, java.util.List.of(v)));
                wrapped = Request.create(
                        request.httpMethod(),
                        request.url(),
                        newHeaders,
                        request.body(),
                        request.charset(),
                        request.requestTemplate());
            }
        }

        try {
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
            hookChain.after(context, RpcCallResult.success(duration));
            return response;
        } catch (IOException | RuntimeException ex) {
            Duration duration = Duration.between(start, Instant.now());
            if (log.isDebugEnabled()) {
                log.debug("RpcFeignClient: HTTP call failed - service={}, method={}, url={}, duration={}ms, error={}",
                        metadata.getService(),
                        metadata.getMethod(),
                        request.url(),
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
