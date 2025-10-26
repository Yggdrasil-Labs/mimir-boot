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
     * 请求追踪ID请求头
     */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /**
     * 用户ID请求头
     */
    public static final String USER_ID_HEADER = "X-User-Id";

    /**
     * 租户ID请求头
     */
    public static final String TENANT_ID_HEADER = "X-Tenant-Id";

    /**
     * 应用ID请求头
     */
    public static final String APP_ID_HEADER = "X-App-Id";

    /**
     * 版本号请求头
     */
    public static final String VERSION_HEADER = "X-Version";

    /**
     * 语言请求头
     */
    public static final String LANGUAGE_HEADER = "X-Language";

    /**
     * 时区请求头
     */
    public static final String TIMEZONE_HEADER = "X-Timezone";

    /**
     * 私有构造方法，防止实例化
     */
    private CommonConstants() {
    }
}
