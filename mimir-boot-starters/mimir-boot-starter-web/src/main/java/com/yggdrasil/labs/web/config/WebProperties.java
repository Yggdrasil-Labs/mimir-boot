package com.yggdrasil.labs.web.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Web 层配置属性
 *
 * <p>功能说明：</p>
 * <ul>
 * <li>统一配置 Web 层的通用特性</li>
 * <li>支持 CORS、序列化和响应增强等配置</li>
 * <li>提供合理地默认值</li>
 * </ul>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "mimir.boot.web")
public class WebProperties {

    /**
     * 是否启用 Web 增强功能
     * 默认：true
     */
    private boolean enabled = true;

    /**
     * CORS 配置
     */
    private Cors cors = new Cors();

    /**
     * 序列化配置
     */
    private Serialization serialization = new Serialization();

    /**
     * 已弃用的安全配置，仅为保持 2.x Java API 与配置绑定兼容而保留。
     */
    @Deprecated(since = "2.1.1", forRemoval = false)
    private Security security = new Security();

    /**
     * 响应增强配置
     */
    private Response response = new Response();

    /**
     * 获取已弃用的安全配置。
     *
     * @return 仅用于兼容的安全配置
     * @deprecated 该配置从未被 starter 消费；上传限制请使用 Spring Boot multipart 配置，XSS 防护由应用负责
     */
    @Deprecated(since = "2.1.1", forRemoval = false)
    @DeprecatedConfigurationProperty(
            reason = "该配置从未被 starter 消费；请使用 Spring Boot multipart 配置和应用侧 XSS 防护",
            since = "2.1.1")
    public Security getSecurity() {
        return security;
    }

    /**
     * 设置已弃用的安全配置。
     *
     * @param security 仅用于兼容的安全配置
     * @deprecated 该配置从未被 starter 消费
     */
    @Deprecated(since = "2.1.1", forRemoval = false)
    public void setSecurity(Security security) {
        this.security = security;
    }

    /**
     * CORS 跨域配置
     */
    @Data
    public static class Cors {
        /**
         * 是否启用 CORS
         * 默认：false
         */
        private boolean enabled = false;

        /**
         * 允许的源
         * 默认：空白名单
         */
        private List<String> allowedOrigins = new ArrayList<>();

        /**
         * 允许的 HTTP 方法
         * 默认：GET, POST, PUT, DELETE, PATCH, OPTIONS
         */
        private List<String> allowedMethods = new ArrayList<>(
                List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
        );

        /**
         * 允许的请求头
         * 默认：*
         */
        private List<String> allowedHeaders = new ArrayList<>(List.of("*"));

        /**
         * 是否允许携带凭证
         * 默认：false
         */
        private boolean allowCredentials = false;

        /**
         * 预检请求的有效期（秒）
         * 默认：3600（1小时）
         */
        private Duration maxAge = Duration.ofHours(1);

        /**
         * 暴露的响应头
         */
        private List<String> exposedHeaders = new ArrayList<>();
    }

    /**
     * 序列化配置
     */
    @Data
    public static class Serialization {
        /**
         * 日期时间格式
         * 默认：yyyy-MM-dd HH:mm:ss
         */
        private String dateTimeFormat = "yyyy-MM-dd HH:mm:ss";

        /**
         * 日期格式
         * 默认：yyyy-MM-dd
         */
        private String dateFormat = "yyyy-MM-dd";

        /**
         * 时间格式
         * 默认：HH:mm:ss
         */
        private String timeFormat = "HH:mm:ss";

        /**
         * 时区
         * 默认：Asia/Shanghai
         */
        private String timeZone = "Asia/Shanghai";

        /**
         * 是否写入空值
         * 默认：false（不写入 null 值）
         */
        private boolean writeNulls = false;

        /**
         * 是否美化输出（格式化 JSON）
         * 默认：false
         */
        private boolean prettyPrint = false;

        /**
         * 是否忽略未知属性
         * 默认：true
         */
        private boolean ignoreUnknownProperties = true;
    }

    /**
     * 已弃用的安全配置载体。
     *
     * @deprecated 这些属性从未被 starter 消费，仅为保持 2.x 兼容而保留
     */
    @Data
    @Deprecated(since = "2.1.1", forRemoval = false)
    public static class Security {
        /**
         * 是否启用安全增强
         * 默认：true
         */
        private boolean enabled = true;

        /**
         * 最大请求大小（MB）
         * 默认：10MB
         */
        private int maxRequestSize = 10;

        /**
         * 单个文件最大大小（MB）
         * 默认：10MB
         */
        private int maxFileSize = 10;

        /**
         * 是否启用 XSS 防护
         * 默认：true
         */
        private boolean xssProtectionEnabled = true;
    }

    /**
     * 响应增强配置
     */
    @Data
    public static class Response {
        /**
         * 是否启用响应增强（自动添加 traceId 等）
         * 默认：true
         */
        private boolean enabled = true;

        /**
         * 是否自动填充 traceId
         * 默认：true
         */
        private boolean autoFillTraceId = true;
    }
}
