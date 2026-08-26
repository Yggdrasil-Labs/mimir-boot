package com.yggdrasil.labs.mybatis.config;

import com.yggdrasil.labs.mybatis.util.MapperPackageDetector;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * MyBatis 配置属性。
 */
@ConfigurationProperties(prefix = "mimir.boot.mybatis")
public class MybatisProperties {

    /**
     * 默认 Mapper 扫描包
     */
    public static final String DEFAULT_MAPPER_PACKAGE = "com.yggdrasil.labs.**.mapper";

    /**
     * Mapper 扫描包，支持多个
     */
    private List<String> mapperPackages = new ArrayList<>();

    /**
     * 是否启用 JSON 结构化 SQL 日志拦截器
     */
    private Boolean enableJsonSqlLog;

    /**
     * 是否启用字段加解密功能，默认 false
     */
    private boolean cryptoEnabled = false;

    /**
     * 加解密密钥（Base64 编码）。启用字段加密时必须配置。
     */
    private String cryptoKey;

    /** 应用级 v2 密文上下文。 */
    private String cryptoContext;

    /** 是否将新写入切换为 v2，默认关闭以支持滚动升级。 */
    private boolean cryptoV2WriteEnabled;

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
    @Deprecated(since = "2.2.1", forRemoval = false)
    public String getFinalMapperPackages() {
        return getEffectiveMapperPackages();
    }

    /**
     * 获取实际交给 MapperScannerConfigurer 的去重扫描包集合。
     */
    public String getEffectiveMapperPackages() {
        Set<String> packages = new LinkedHashSet<>();
        packages.add(DEFAULT_MAPPER_PACKAGE);
        if (!CollectionUtils.isEmpty(mapperPackages)) {
            packages.addAll(mapperPackages);
        }
        packages.addAll(MapperPackageDetector.detectMapperPackages());
        return String.join(",", packages);
    }

    public Boolean getEnableJsonSqlLog() {
        return enableJsonSqlLog;
    }

    public void setEnableJsonSqlLog(Boolean enableJsonSqlLog) {
        this.enableJsonSqlLog = enableJsonSqlLog;
    }

    public boolean isCryptoEnabled() {
        return cryptoEnabled;
    }

    public void setCryptoEnabled(boolean cryptoEnabled) {
        this.cryptoEnabled = cryptoEnabled;
    }

    public String getCryptoKey() {
        return cryptoKey;
    }

    public void setCryptoKey(String cryptoKey) {
        this.cryptoKey = cryptoKey;
    }

    public String getCryptoContext() {
        return cryptoContext;
    }

    public void setCryptoContext(String cryptoContext) {
        this.cryptoContext = cryptoContext;
    }

    public boolean isCryptoV2WriteEnabled() {
        return cryptoV2WriteEnabled;
    }

    public void setCryptoV2WriteEnabled(boolean cryptoV2WriteEnabled) {
        this.cryptoV2WriteEnabled = cryptoV2WriteEnabled;
    }
}
