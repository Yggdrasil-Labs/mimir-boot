package com.yggdrasil.labs.common.util;

import com.yggdrasil.labs.common.constant.CommonConstants;
import com.yggdrasil.labs.common.constant.HttpHeaderConstants;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 客户端 IP 解析工具。
 * <p>无 servlet 直接依赖，通过函数式接口传入 header 获取与 remoteAddr 供应器。</p>
 */
public final class IpUtils {

    private static final String UNKNOWN = CommonConstants.UNKNOWN;

    private IpUtils() {
    }

    /**
     * 解析客户端真实 IP。
     * 优先级：X-Forwarded-For（取首个） -> X-Real-IP -> Proxy-Client-IP -> WL-Proxy-Client-IP -> remoteAddr。
     *
     * @param headerGetter       header 获取函数，如 request::getHeader
     * @param remoteAddrSupplier 兜底 remoteAddr 供应器，如 request::getRemoteAddr
     * @return 客户端真实 IP
     */
    public static String resolveClientIp(Function<String, String> headerGetter, Supplier<String> remoteAddrSupplier) {
        String[] candidateHeaders = new String[]{
                HttpHeaderConstants.X_FORWARDED_FOR,
                HttpHeaderConstants.X_REAL_IP,
                HttpHeaderConstants.PROXY_CLIENT_IP,
                HttpHeaderConstants.WL_PROXY_CLIENT_IP
        };

        for (String header : candidateHeaders) {
            String extracted = extractFromHeader(headerGetter.apply(header), header);
            if (extracted != null && !extracted.isEmpty()) {
                return extracted;
            }
        }
        return remoteAddrSupplier.get();
    }

    private static String extractFromHeader(String value, String headerName) {
        if (value == null || value.isEmpty() || UNKNOWN.equalsIgnoreCase(value)) {
            return null;
        }
        if (HttpHeaderConstants.X_FORWARDED_FOR.equalsIgnoreCase(headerName)) {
            int index = value.indexOf(',');
            if (index != -1) {
                value = value.substring(0, index);
            }
        }
        return value.trim();
    }
}
