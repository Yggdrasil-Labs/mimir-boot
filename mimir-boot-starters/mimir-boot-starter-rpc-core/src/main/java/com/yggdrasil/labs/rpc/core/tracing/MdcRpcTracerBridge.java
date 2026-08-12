package com.yggdrasil.labs.rpc.core.tracing;

import com.yggdrasil.labs.common.constant.CommonConstants;
import com.yggdrasil.labs.common.constant.HttpHeaderConstants;
import com.yggdrasil.labs.rpc.core.context.RpcCallContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

/**
 * 基于 SLF4J MDC 的默认 RPC Trace 上下文传播实现。
 */
public class MdcRpcTracerBridge implements RpcTracerBridge {

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    @Override
    public Map<String, String> inject(RpcCallContext context) {
        Map<String, String> carrier = new LinkedHashMap<>();
        putIfValid(carrier, HttpHeaderConstants.TRACE_ID_HEADER, MDC.get(CommonConstants.TRACE_ID));
        putIfValid(carrier, HttpHeaderConstants.REQUEST_ID_HEADER, MDC.get(CommonConstants.REQUEST_ID));
        return carrier;
    }

    @Override
    public void extract(RpcCallContext context, Map<String, String> carrier) {
        applyCarrier(carrier);
    }

    @Override
    public RpcTraceScope extractScope(RpcCallContext context, Map<String, String> carrier) {
        String previousTraceId = MDC.get(CommonConstants.TRACE_ID);
        String previousRequestId = MDC.get(CommonConstants.REQUEST_ID);
        applyCarrier(carrier);
        return new MdcScope(previousTraceId, previousRequestId);
    }

    private void applyCarrier(Map<String, String> carrier) {
        String traceId = carrierValue(carrier, HttpHeaderConstants.TRACE_ID_HEADER);
        String requestId = carrierValue(carrier, HttpHeaderConstants.REQUEST_ID_HEADER);
        MDC.put(CommonConstants.TRACE_ID, isValidIdentifier(traceId) ? traceId : generateIdentifier());
        if (isValidIdentifier(requestId)) {
            MDC.put(CommonConstants.REQUEST_ID, requestId);
        } else {
            MDC.remove(CommonConstants.REQUEST_ID);
        }
    }

    private void putIfValid(Map<String, String> carrier, String key, String value) {
        if (isValidIdentifier(value)) {
            carrier.put(key, value);
        }
    }

    private String carrierValue(Map<String, String> carrier, String key) {
        return carrier == null ? null : carrier.get(key);
    }

    public static boolean isValidIdentifier(String value) {
        return StringUtils.hasText(value) && IDENTIFIER_PATTERN.matcher(value).matches();
    }

    private String generateIdentifier() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static final class MdcScope implements RpcTraceScope {

        private final String previousTraceId;
        private final String previousRequestId;
        private final AtomicBoolean closed = new AtomicBoolean();

        private MdcScope(String previousTraceId, String previousRequestId) {
            this.previousTraceId = previousTraceId;
            this.previousRequestId = previousRequestId;
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            restore(CommonConstants.TRACE_ID, previousTraceId);
            restore(CommonConstants.REQUEST_ID, previousRequestId);
        }

        private void restore(String key, String value) {
            if (value == null) {
                MDC.remove(key);
            } else {
                MDC.put(key, value);
            }
        }
    }
}
