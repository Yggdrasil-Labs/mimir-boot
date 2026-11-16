package com.yggdrasil.labs.log.json;

import java.util.Set;

/** JSON 日志的内部键与受保护字段集合（内部实现，非公共 API）。 */
public final class LogJsonKeys {

    public static final String INTERNAL_FORMAT = "mimir.boot.log.internal.format";
    public static final String INTERNAL_CAPTURE_ACTIVE =
            "mimir.boot.log.internal.json.capture-active";
    public static final String INTERNAL_ENVIRONMENT = "mimir.boot.log.internal.json.environment";
    public static final String INTERNAL_INSTANCE = "mimir.boot.log.internal.json.instance";
    public static final String INTERNAL_EXCLUDE_MDC_KEYS =
            "mimir.boot.log.internal.json.exclude-mdc-keys";
    public static final String INTERNAL_FIELD_LENGTH_LIMITS =
            "mimir.boot.log.internal.json.field-length-limits";

    public static final String FORMAT = "mimir.boot.log.json.format";
    public static final String SERVICE = "mimir.boot.log.json.service";
    public static final String ENVIRONMENT = "mimir.boot.log.json.environment";
    public static final String INSTANCE = "mimir.boot.log.json.instance";
    public static final String EXCLUDE_MDC_KEYS = "mimir.boot.log.json.excludeMdcKeys";
    public static final String FIELD_LENGTH_LIMITS = "mimir.boot.log.json.fieldLengthLimits";
    public static final String RESERVED_KEY = "mimir.log.fields";

    public static final String METHOD = "method";
    public static final String PATH = "path";
    public static final String STATUS_CODE = "statusCode";
    public static final String DURATION_MS = "durationMs";
    public static final String CLIENT_IP = "clientIp";
    public static final String USER_AGENT = "userAgent";
    public static final String OUTCOME = "outcome";
    public static final String ERROR_TYPE = "errorType";
    public static final String SLOW = "slow";

    public static final Set<String> TOP_LEVEL_FIELDS =
            Set.of(
                    "timestamp",
                    "level",
                    "service",
                    "environment",
                    "instance",
                    "thread",
                    "logger",
                    "message",
                    "traceId",
                    "spanId",
                    "context",
                    "attributes",
                    "exception",
                    "processing");

    public static final Set<String> ACCESS_FIELDS =
            Set.of(
                    METHOD,
                    PATH,
                    STATUS_CODE,
                    DURATION_MS,
                    CLIENT_IP,
                    USER_AGENT,
                    OUTCOME,
                    ERROR_TYPE,
                    SLOW);

    private LogJsonKeys() {}
}
