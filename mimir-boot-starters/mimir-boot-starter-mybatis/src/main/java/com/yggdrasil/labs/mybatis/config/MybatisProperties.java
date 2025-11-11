package com.yggdrasil.labs.mybatis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * MyBatis 配置属性。
 */
@ConfigurationProperties(prefix = "mimir.mybatis")
public class MybatisProperties {

    /** 默认 Mapper 扫描包 */
    public static final String DEFAULT_MAPPER_PACKAGE = "com.yggdrasil.labs.**.mapper";

    /** Mapper 扫描包，支持多个 */
    private List<String> mapperPackages = new ArrayList<>();

    /** 是否启用控制台SQL日志（优先根据环境自动判断，可被此配置覆盖） */
    private Boolean enableSqlStdout;

    /** 是否启用 JSON 结构化 SQL 日志拦截器 */
    private Boolean enableJsonSqlLog;

    /** 加解密密钥（Base64编码），未配置时自动生成（仅用于开发测试） */
    private String cryptoKey;

    public List<String> getMapperPackages() {
        return mapperPackages;
    }

    public void setMapperPackages(List<String> mapperPackages) {
        this.mapperPackages = mapperPackages;
    }

    /**
     * 获取最终的 Mapper 扫描包列表（包含默认包和用户配置的包）。
     * 默认包始终包含，用户配置的包会追加到列表中。
     *
     * @return 最终的扫描包列表，用逗号分隔的字符串
     */
    public String getFinalMapperPackages() {
        Set<String> packages = new LinkedHashSet<>();
        // 始终包含默认包
        packages.add(DEFAULT_MAPPER_PACKAGE);
        // 添加用户配置的包
        if (!CollectionUtils.isEmpty(mapperPackages)) {
            packages.addAll(mapperPackages);
        }
        return String.join(",", packages);
    }

    public Boolean getEnableSqlStdout() {
        return enableSqlStdout;
    }

    public void setEnableSqlStdout(Boolean enableSqlStdout) {
        this.enableSqlStdout = enableSqlStdout;
    }

    public Boolean getEnableJsonSqlLog() {
        return enableJsonSqlLog;
    }

    public void setEnableJsonSqlLog(Boolean enableJsonSqlLog) {
        this.enableJsonSqlLog = enableJsonSqlLog;
    }

    public String getCryptoKey() {
        return cryptoKey;
    }

    public void setCryptoKey(String cryptoKey) {
        this.cryptoKey = cryptoKey;
    }
}


