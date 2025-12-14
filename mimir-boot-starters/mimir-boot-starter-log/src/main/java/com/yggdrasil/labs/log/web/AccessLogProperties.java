package com.yggdrasil.labs.log.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 访问日志配置属性
 *
 * <p>用于配置访问日志功能</p>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "mimir.boot.log.access")
public class AccessLogProperties {

    /**
     * 是否启用访问日志功能
     * 默认：true
     */
    private boolean enabled = true;

    /**
     * 慢接口阈值（毫秒）
     * 超过此耗时的接口将被记录为 WARN 级别
     * 默认：3000ms（3秒）
     */
    private long slowThresholdMs = 3000;

    /**
     * 排除的路径模式列表（Ant 风格路径匹配）
     * 匹配这些路径的请求将不记录访问日志
     * 
     * <p>默认预置的排除路径：</p>
     * <ul>
     * <li>{@code /actuator/**} - Spring Boot Actuator 监控端点</li>
     * <li>{@code /favicon.ico} - 浏览器图标请求</li>
     * <li>{@code /static/**} - 静态资源路径</li>
     * <li>{@code /public/**} - 公共资源路径</li>
     * <li>{@code /assets/**} - 资源文件路径</li>
     * <li>{@code /css/**} - CSS 样式文件</li>
     * <li>{@code /js/**} - JavaScript 文件</li>
     * <li>{@code /images/**} - 图片资源</li>
     * <li>{@code /webjars/**} - WebJars 资源</li>
     * <li>{@code /error} - 错误页面</li>
     * </ul>
     * 
     * <p>自定义配置示例：</p>
     * <pre>
     * mimir:
     *   boot:
     *     log:
     *       access:
     *         exclude-paths:
     *           - /actuator/**
     *           - /health
     *           - /favicon.ico
     *           - /api/public/**
     * </pre>
     * 
     * <p>注意：如果配置了 exclude-paths，将完全替换默认值。如需保留默认值并添加新路径，需要显式列出所有路径。</p>
     */
    private List<String> excludePaths = null;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getSlowThresholdMs() {
        return slowThresholdMs;
    }

    public void setSlowThresholdMs(long slowThresholdMs) {
        this.slowThresholdMs = slowThresholdMs;
    }

    public List<String> getExcludePaths() {
        if (excludePaths == null) {
            excludePaths = new ArrayList<>(Arrays.asList(
                    "/actuator/**",      // Spring Boot Actuator 监控端点
                    "/favicon.ico",      // 浏览器图标请求
                    "/static/**",         // 静态资源路径
                    "/public/**",         // 公共资源路径
                    "/assets/**",         // 资源文件路径
                    "/css/**",            // CSS 样式文件
                    "/js/**",             // JavaScript 文件
                    "/images/**",         // 图片资源
                    "/webjars/**",        // WebJars 资源
                    "/error"              // 错误页面
            ));
        }
        return excludePaths;
    }

    public void setExcludePaths(List<String> excludePaths) {
        this.excludePaths = excludePaths != null ? new ArrayList<>(excludePaths) : null;
    }
}

