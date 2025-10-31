package com.yggdrasil.labs.mybatis.config;

import com.yggdrasil.labs.mybatis.crypto.CryptoKeyProvider;
import com.yggdrasil.labs.mybatis.crypto.CryptoUtils;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * MyBatis-Plus 加解密相关自动配置。
 *
 * <p>默认提供一个 {@link com.yggdrasil.labs.mybatis.crypto.CryptoKeyProvider}，
 * 当未显式配置密钥时，为开发/测试目的自动生成临时密钥（生产环境请务必显式配置）。
 * 同时暴露若干通用 {@code TypeHandler} 以便按类型自动处理加解密。
 */
@AutoConfiguration
@EnableConfigurationProperties(MybatisProperties.class)
public class MybatisPlusCryptoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CryptoKeyProvider.class)
    public CryptoKeyProvider defaultCryptoKeyProvider(MybatisProperties properties) {
        String configured = properties.getCryptoKey();
        final String key = (configured == null || configured.isEmpty())
            ? CryptoUtils.generateKey()
            : configured;
        return () -> key;
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

