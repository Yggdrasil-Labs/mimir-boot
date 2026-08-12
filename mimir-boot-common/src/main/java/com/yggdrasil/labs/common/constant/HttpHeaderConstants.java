package com.yggdrasil.labs.common.constant;

/**
 * HTTP 请求头常量
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
public final class HttpHeaderConstants {

    private HttpHeaderConstants() {
    }

    // 业务相关请求头
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String TENANT_ID_HEADER = "X-Tenant-Id";
    public static final String APP_ID_HEADER = "X-App-Id";
    public static final String VERSION_HEADER = "X-Version";
    public static final String LANGUAGE_HEADER = "X-Language";
    public static final String TIMEZONE_HEADER = "X-Timezone";

    // 常见代理透传客户端 IP 的请求头
    public static final String X_FORWARDED_FOR = "X-Forwarded-For";
    public static final String X_REAL_IP = "X-Real-IP";
    public static final String PROXY_CLIENT_IP = "Proxy-Client-IP";
    public static final String WL_PROXY_CLIENT_IP = "WL-Proxy-Client-IP";
}

