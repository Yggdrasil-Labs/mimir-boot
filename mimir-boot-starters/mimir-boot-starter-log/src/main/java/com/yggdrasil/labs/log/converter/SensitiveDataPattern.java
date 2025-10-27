package com.yggdrasil.labs.log.converter;

/**
 * 预置敏感信息脱敏规则枚举
 * 
 * <p>提供了常见的敏感信息脱敏规则，用户可以选择启用</p>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
public enum SensitiveDataPattern {

    /** 密码相关字段 */
    PASSWORD("password", "(?i)(password|pwd|passwd|密码)\\s*[=:]\\s*['\"]?[^'\"\\s]+"),
    
    /** Token 相关字段 */
    TOKEN("token", "(?i)(token|access_token|refresh_token)\\s*[=:]\\s*['\"]?[^'\"\\s]+"),
    
    /** 密钥相关字段 */
    SECRET("secret", "(?i)(secret|private_key|公钥)\\s*[=:]\\s*['\"]?[^'\"\\s]+"),
    
    /** API Key 相关字段 */
    API_KEY("api_key", "(?i)(apikey|api_key|app_key)\\s*[=:]\\s*['\"]?[^'\"\\s]+"),
    
    /** 账号相关字段 */
    ACCOUNT("account", "(?i)(account|accountId|account_id|账号)\\s*[=:]\\s*['\"]?[^'\"\\s]+"),
    
    /** 身份证相关字段 */
    ID_CARD("id_card", "(?i)(idcard|id_card|身份证)\\s*[=:]\\s*['\"]?[^'\"\\s]+"),
    
    /** 手机号相关字段 */
    PHONE("phone", "(?i)(phone|mobile|tel|手机|电话)\\s*[=:]\\s*['\"]?[^'\"\\s]+"),
    
    /** 银行卡相关字段 */
    BANK_CARD("bank_card", "(?i)(bankcard|bank_card|银行卡)\\s*[=:]\\s*['\"]?[^'\"\\s]+"),
    
    /** 邮箱相关字段 */
    EMAIL("email", "(?i)(email|mail)\\s*[=:]\\s*['\"]?[^'\"\\s]+"),
    
    /** 姓名相关字段 */
    NAME("name", "(?i)(name|realname|真实姓名)\\s*[=:]\\s*['\"]?[^'\"\\s]+"),
    
    /** 纯身份证号（15位或18位） */
    ID_CARD_NUMBER("id_card_number", "\\d{15}|\\d{18}"),
    
    /** 纯手机号（11位） */
    PHONE_NUMBER("phone_number", "1[3-9]\\d{9}"),
    
    /** 纯银行卡号（16-19位） */
    BANK_CARD_NUMBER("bank_card_number", "\\d{16,19}"),
    
    /** 纯邮箱地址 */
    EMAIL_ADDRESS("email_address", "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    /** 规则名称 */
    private final String name;
    
    /** 正则表达式 */
    private final String pattern;

    SensitiveDataPattern(String name, String pattern) {
        this.name = name;
        this.pattern = pattern;
    }

    public String getName() {
        return name;
    }

    public String getPattern() {
        return pattern;
    }

    /**
     * 根据名称查找枚举
     */
    public static SensitiveDataPattern fromName(String name) {
        for (SensitiveDataPattern pattern : values()) {
            if (pattern.name.equalsIgnoreCase(name)) {
                return pattern;
            }
        }
        return null;
    }
}

