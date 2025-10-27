package com.yggdrasil.labs.log.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 日志脱敏配置属性
 *
 * <p>用于配置日志敏感信息脱敏功能</p>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "mimir.boot.log.mask")
public class LogMaskProperties {

    /**
     * 启用的预置脱敏规则（按名称）
     * 可用值：password, token, secret, api_key, account, id_card, phone, bank_card, email, name, id_card_number, phone_number, bank_card_number, email_address
     */
    private List<String> enabledPatterns = new ArrayList<>();

    /**
     * 自定义脱敏规则（正则表达式）
     * 与预置规则一起使用
     */
    private List<String> customPatterns = new ArrayList<>();

    /**
     * 替换字符（默认：******）
     */
    private String replacement = "******";

    public List<String> getEnabledPatterns() {
        return enabledPatterns;
    }

    public void setEnabledPatterns(List<String> enabledPatterns) {
        this.enabledPatterns = enabledPatterns;
    }

    public List<String> getCustomPatterns() {
        return customPatterns;
    }

    public void setCustomPatterns(List<String> customPatterns) {
        this.customPatterns = customPatterns;
    }

    public String getReplacement() {
        return replacement;
    }

    public void setReplacement(String replacement) {
        this.replacement = replacement;
    }
}

