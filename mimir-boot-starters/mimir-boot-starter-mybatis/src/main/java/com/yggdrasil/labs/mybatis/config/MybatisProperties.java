package com.yggdrasil.labs.mybatis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * MyBatis 配置属性。
 */
@ConfigurationProperties(prefix = "mimir.mybatis")
public class MybatisProperties {

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


