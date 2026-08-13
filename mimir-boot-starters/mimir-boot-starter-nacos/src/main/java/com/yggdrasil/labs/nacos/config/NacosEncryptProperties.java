package com.yggdrasil.labs.nacos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nacos 配置加密属性
 *
 * <p>功能说明：</p>
 * <ul>
 * <li>支持配置加密密钥</li>
 * <li>新密文固定使用 AES-GCM；旧 AES 密文仅支持通过显式的迁移 API 离线读取</li>
 * <li>支持启用/禁用配置解密功能</li>
 * </ul>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = NacosEncryptProperties.PREFIX)
public class NacosEncryptProperties {

    public static final String PREFIX = "mimir.boot.nacos.encrypt";
    public static final String LEGACY_PREFIX = "mimir.nacos.encrypt";

    /** 是否启用配置加密脱敏功能 */
    private Boolean enabled = true;

    /** 加密密钥（Base64编码），用于解密配置值 */
    private String key;

    /**
     * 加密算法。
     *
     * @deprecated 应用配置固定使用 AES-GCM；旧 AES 密文只能在离线迁移工具中显式读取。
     */
    @Deprecated(since = "2.1.1", forRemoval = false)
    private String algorithm = "AES/GCM/NoPadding";

    /** ENC() 标记前缀，默认 ENC */
    private String encryptedValuePrefix = "ENC";

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getPrefix() {
        return encryptedValuePrefix;
    }

    public void setPrefix(String prefix) {
        this.encryptedValuePrefix = prefix;
    }
}
