package com.yggdrasil.labs.mybatis.config;

import com.yggdrasil.labs.mybatis.crypto.CryptoKeyProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

import java.util.Base64;

/**
 * MyBatis-Plus 加解密相关自动配置。
 *
 * <p>默认提供一个 {@link com.yggdrasil.labs.mybatis.crypto.CryptoKeyProvider}。
 * 启用字段加密时必须配置稳定密钥，避免重启后无法解密已持久化数据。
 * 同时暴露若干通用 {@code TypeHandler} 以便按类型自动处理加解密。
 *
 * <p>需要显式配置 {@code mimir.boot.mybatis.crypto-enabled=true} 才会启用加密功能。
 */
@AutoConfiguration
@EnableConfigurationProperties(MybatisProperties.class)
@ConditionalOnProperty(prefix = "mimir.boot.mybatis", name = "crypto-enabled", havingValue = "true")
public class MybatisPlusCryptoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CryptoKeyProvider.class)
    public CryptoKeyProvider defaultCryptoKeyProvider(MybatisProperties properties) {
        String configured = properties.getCryptoKey();
        if (!StringUtils.hasText(configured)) {
            throw new IllegalStateException(
                    "启用 MyBatis 字段加密时必须配置 mimir.boot.mybatis.crypto-key，"
                            + "或提供自定义 CryptoKeyProvider");
        }
        validateCryptoKey(configured);
        return () -> configured;
    }

    private static void validateCryptoKey(String key) {
        try {
            int keyLength = Base64.getDecoder().decode(key).length;
            if (keyLength != 16 && keyLength != 24 && keyLength != 32) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "mimir.boot.mybatis.crypto-key 必须是有效的 Base64 AES 密钥（128、192 或 256 位）");
        }
    }

    @Bean
    public com.yggdrasil.labs.mybatis.typehandler.StringCryptoTypeHandler stringCryptoTypeHandler(
            CryptoKeyProvider keyProvider) {
        return new com.yggdrasil.labs.mybatis.typehandler.StringCryptoTypeHandler(keyProvider);
    }

    @Bean
    public com.yggdrasil.labs.mybatis.typehandler.LongCryptoTypeHandler longCryptoTypeHandler(
            CryptoKeyProvider keyProvider) {
        return new com.yggdrasil.labs.mybatis.typehandler.LongCryptoTypeHandler(keyProvider);
    }

    @Bean
    public com.yggdrasil.labs.mybatis.typehandler.IntegerCryptoTypeHandler integerCryptoTypeHandler(
            CryptoKeyProvider keyProvider) {
        return new com.yggdrasil.labs.mybatis.typehandler.IntegerCryptoTypeHandler(keyProvider);
    }
}
