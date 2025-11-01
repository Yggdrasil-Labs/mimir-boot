package com.yggdrasil.labs.mybatis.config;

import com.yggdrasil.labs.mybatis.crypto.CryptoKeyProvider;
import com.yggdrasil.labs.mybatis.typehandler.IntegerCryptoTypeHandler;
import com.yggdrasil.labs.mybatis.typehandler.LongCryptoTypeHandler;
import com.yggdrasil.labs.mybatis.typehandler.StringCryptoTypeHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MyBatis-Plus 加解密配置测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class MybatisPlusCryptoConfigurationTest {

    private MybatisPlusCryptoConfiguration configuration;
    private MybatisProperties properties;

    @BeforeEach
    void setUp() {
        configuration = new MybatisPlusCryptoConfiguration();
        properties = new MybatisProperties();
    }

    @Test
    void testDefaultCryptoKeyProviderWithConfiguredKey() {
        properties.setCryptoKey("test-crypto-key-12345");
        CryptoKeyProvider provider = configuration.defaultCryptoKeyProvider(properties);

        assertNotNull(provider);
        assertEquals("test-crypto-key-12345", provider.getKey());
    }

    @Test
    void testDefaultCryptoKeyProviderWithEmptyKey() {
        properties.setCryptoKey("");
        CryptoKeyProvider provider = configuration.defaultCryptoKeyProvider(properties);

        assertNotNull(provider);
        assertNotNull(provider.getKey());
        assertFalse(provider.getKey().isEmpty());
    }

    @Test
    void testDefaultCryptoKeyProviderWithNullKey() {
        properties.setCryptoKey(null);
        CryptoKeyProvider provider = configuration.defaultCryptoKeyProvider(properties);

        assertNotNull(provider);
        assertNotNull(provider.getKey());
        assertFalse(provider.getKey().isEmpty());
    }

    @Test
    void testStringCryptoTypeHandlerCreation() {
        CryptoKeyProvider keyProvider = () -> "test-key";
        StringCryptoTypeHandler handler = configuration.stringCryptoTypeHandler(keyProvider);

        assertNotNull(handler);
        assertInstanceOf(StringCryptoTypeHandler.class, handler);
    }

    @Test
    void testLongCryptoTypeHandlerCreation() {
        CryptoKeyProvider keyProvider = () -> "test-key";
        LongCryptoTypeHandler handler = configuration.longCryptoTypeHandler(keyProvider);

        assertNotNull(handler);
        assertInstanceOf(LongCryptoTypeHandler.class, handler);
    }

    @Test
    void testIntegerCryptoTypeHandlerCreation() {
        CryptoKeyProvider keyProvider = () -> "test-key";
        IntegerCryptoTypeHandler handler = configuration.integerCryptoTypeHandler(keyProvider);

        assertNotNull(handler);
        assertInstanceOf(IntegerCryptoTypeHandler.class, handler);
    }
}

