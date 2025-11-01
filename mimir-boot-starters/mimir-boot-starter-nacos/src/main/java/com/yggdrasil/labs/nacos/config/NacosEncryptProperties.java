package com.yggdrasil.labs.nacos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nacos 配置加密属性
 *
 * <p>功能说明：</p>
 * <ul>
 * <li>支持配置加密密钥</li>
 * <li>支持自定义加密算法</li>
 * <li>支持启用/禁用配置解密功能</li>
 * </ul>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "mimir.nacos.encrypt")
public class NacosEncryptProperties {

    /** 是否启用配置加密脱敏功能 */
    private Boolean enabled = true;

    /** 加密密钥（Base64编码），用于解密配置值 */
    private String key;

    /** 加密算法，默认 AES */
    private String algorithm = "AES";

    /** ENC() 标记前缀，默认 ENC */
    private String prefix = "ENC";

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
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
}
