package com.yggdrasil.labs.common.constant;

/**
 * 通用常量
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
public class CommonConstants {

    /**
     * 默认页码
     */
    public static final Long DEFAULT_PAGE_NUMBER = 1L;

    /**
     * 默认每页大小
     */
    public static final Long DEFAULT_PAGE_SIZE = 10L;

    /**
     * 最大每页大小
     */
    public static final Long MAX_PAGE_SIZE = 1000L;

    /**
     * 默认字符编码
     */
    public static final String DEFAULT_CHARSET = "UTF-8";

    /**
     * 默认日期格式
     */
    public static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd";

    /**
     * 默认时间格式
     */
    public static final String DEFAULT_TIME_FORMAT = "HH:mm:ss";

    /**
     * 默认日期时间格式
     */
    public static final String DEFAULT_DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    /**
     * 默认时区
     */
    public static final String DEFAULT_TIMEZONE = "Asia/Shanghai";

    /**
     * MDC/字段中的 traceId 关键字
     */
    public static final String TRACE_ID = "traceId";

    /**
     * 通用未知占位
     */
    public static final String UNKNOWN = "unknown";

    /**
     * 通用脱敏占位
     */
    public static final String MASKED = "****";

    /**
     * 私有构造方法，防止实例化
     */
    private CommonConstants() {
    }
}
