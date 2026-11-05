package com.yggdrasil.labs.exception.handler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;

import lombok.extern.slf4j.Slf4j;

/** 在调用业务工厂前确定的 HTTP 元信息，仅在处理器内部使用。 */
@Slf4j
record ExceptionHttpMetadata(
        HttpStatusCode status,
        HttpHeaders headers,
        String code,
        String message,
        Set<String> excluded) {

    private static final Pattern HEADER_NAME = Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]+");

    private static final Set<String> EXCLUDED_HEADERS =
            Set.of(
                    "connection",
                    "keep-alive",
                    "proxy-connection",
                    "te",
                    "trailer",
                    "transfer-encoding",
                    "upgrade",
                    "proxy-authenticate",
                    "proxy-authorization",
                    "content-length",
                    "content-encoding");

    ExceptionHttpMetadata(HttpStatusCode status, HttpHeaders headers, String code, String message) {
        this(status, headers, code, message, excludedHeaders(headers));
    }

    ExceptionHttpMetadata {
        excluded = Set.copyOf(excluded);
        // 工厂执行前快照异常头，避免后续修改影响已解析的协议元信息。
        headers = HttpHeaders.readOnlyHttpHeaders(copyHeaders(headers, true, excluded));
    }

    boolean forbidsBody() {
        int value = status.value();
        return status.is1xxInformational() || value == 204 || value == 205 || value == 304;
    }

    HttpHeaders mergeHeaders(HttpHeaders factoryHeaders) {
        // 两侧 Connection 声明的逐跳字段均先过滤，再由异常头整体覆盖工厂同名头。
        Set<String> allExcluded = new HashSet<>(excluded);
        allExcluded.addAll(excludedHeaders(factoryHeaders));
        HttpHeaders merged = copyHeaders(factoryHeaders, false, allExcluded);
        copyHeaders(headers, true, allExcluded)
                .forEach((name, values) -> merged.put(name, new ArrayList<>(values)));
        return merged;
    }

    private static Set<String> excludedHeaders(HttpHeaders source) {
        Set<String> excluded = new HashSet<>(EXCLUDED_HEADERS);
        if (source == null) return excluded;
        // Connection 指定的动态逐跳字段也不能进入最终响应。
        source.forEach(
                (name, values) -> {
                    if (HttpHeaders.CONNECTION.equalsIgnoreCase(name) && values != null) {
                        for (String value : values) {
                            if (validValue(value)) {
                                for (String token : value.split(",")) {
                                    excluded.add(token.trim().toLowerCase(Locale.ROOT));
                                }
                            }
                        }
                    }
                });
        return excluded;
    }

    private static HttpHeaders copyHeaders(
            HttpHeaders source, boolean exceptionHeaders, Set<String> excluded) {
        HttpHeaders result = new HttpHeaders();
        if (source == null || source.isEmpty()) {
            return result;
        }
        source.forEach(
                (name, values) -> {
                    if (name == null || !HEADER_NAME.matcher(name).matches()) {
                        log.warn("已丢弃非法 HTTP 头名");
                        return;
                    }
                    if (excluded.contains(name.toLowerCase(Locale.ROOT))
                            || (exceptionHeaders && "content-type".equalsIgnoreCase(name))
                            || values == null) {
                        return;
                    }
                    for (String value : values) {
                        if (validValue(value)) {
                            result.add(name, value);
                        } else {
                            log.warn("已丢弃非法 HTTP 头值");
                        }
                    }
                });
        return result;
    }

    private static boolean validValue(String value) {
        return value != null
                && value.indexOf('\r') < 0
                && value.indexOf('\n') < 0
                && value.indexOf('\0') < 0;
    }
}
