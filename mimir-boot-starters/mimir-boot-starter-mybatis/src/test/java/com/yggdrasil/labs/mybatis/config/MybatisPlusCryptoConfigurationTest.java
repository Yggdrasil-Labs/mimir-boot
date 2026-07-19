package com.yggdrasil.labs.mybatis.config;

import com.yggdrasil.labs.mybatis.crypto.CryptoKeyProvider;
import com.yggdrasil.labs.mybatis.crypto.CryptoUtils;
import com.yggdrasil.labs.mybatis.typehandler.IntegerCryptoTypeHandler;
import com.yggdrasil.labs.mybatis.typehandler.LongCryptoTypeHandler;
import com.yggdrasil.labs.mybatis.typehandler.StringCryptoTypeHandler;
import com.yggdrasil.labs.test.base.BaseUnitTest;
import com.yggdrasil.labs.test.util.AssertUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MyBatis-Plus 加解密配置测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class MybatisPlusCryptoConfigurationTest extends BaseUnitTest {

    private static final String CUSTOM_CRYPTO_KEY = CryptoUtils.generateKey();

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MybatisPlusCryptoConfiguration.class));

    private MybatisPlusCryptoConfiguration configuration;
    private MybatisProperties properties;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();
        configuration = new MybatisPlusCryptoConfiguration();
        properties = new MybatisProperties();
    }

    @Test
    void testDefaultCryptoKeyProviderWithConfiguredKey() {
        String key = CryptoUtils.generateKey();
        properties.setCryptoKey(key);
        CryptoKeyProvider provider = configuration.defaultCryptoKeyProvider(properties);

        assertNotNull(provider);
        AssertUtils.assertEquals(key, provider.getKey());
    }

    @Test
    void shouldFailWhenCryptoKeyIsEmpty() {
        properties.setCryptoKey("");

        assertThrows(IllegalStateException.class,
                () -> configuration.defaultCryptoKeyProvider(properties));
    }

    @Test
    void shouldFailWhenCryptoKeyIsMissing() {
        properties.setCryptoKey(null);

        assertThrows(IllegalStateException.class,
                () -> configuration.defaultCryptoKeyProvider(properties));
    }

    @Test
    void shouldFailWhenCryptoKeyIsNotBase64Encoded() {
        properties.setCryptoKey("not-base64");

        assertThrows(IllegalStateException.class,
                () -> configuration.defaultCryptoKeyProvider(properties));
    }

    @Test
    void shouldFailWhenCryptoKeyHasInvalidAesLength() {
        properties.setCryptoKey("dGVzdA==");

        assertThrows(IllegalStateException.class,
                () -> configuration.defaultCryptoKeyProvider(properties));
    }

    @Test
    void shouldFailToStartWhenCryptoIsEnabledWithoutKey() {
        runner.withPropertyValues("mimir.boot.mybatis.crypto-enabled=true")
                .run(context -> {
                    org.assertj.core.api.Assertions.assertThat(context).hasFailed();
                    org.assertj.core.api.Assertions.assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class);
                });
    }

    @Test
    void shouldFailToStartWhenCryptoKeyIsInvalid() {
        runner.withPropertyValues(
                        "mimir.boot.mybatis.crypto-enabled=true",
                        "mimir.boot.mybatis.crypto-key=dGVzdA==")
                .run(context -> {
                    org.assertj.core.api.Assertions.assertThat(context).hasFailed();
                    org.assertj.core.api.Assertions.assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class);
                });
    }

    @Test
    void shouldStartWithCustomCryptoKeyProviderWhenCryptoKeyIsMissing() {
        runner.withUserConfiguration(CustomCryptoKeyProviderConfiguration.class)
                .withPropertyValues("mimir.boot.mybatis.crypto-enabled=true")
                .run(context -> {
                    org.assertj.core.api.Assertions.assertThat(context).hasNotFailed();
                    org.assertj.core.api.Assertions.assertThat(context)
                            .hasSingleBean(CryptoKeyProvider.class);
                    AssertUtils.assertEquals(
                            CUSTOM_CRYPTO_KEY, context.getBean(CryptoKeyProvider.class).getKey());
                });
    }

    @Test
    void shouldDecryptValueWrittenBeforeRestartWithSameKey() throws Exception {
        String key = CryptoUtils.generateKey();
        AtomicReference<String> persistedValue = new AtomicReference<>();

        runner.withPropertyValues(
                        "mimir.boot.mybatis.crypto-enabled=true",
                        "mimir.boot.mybatis.crypto-key=" + key)
                .run(firstContext -> {
                    StringCryptoTypeHandler writer = firstContext.getBean(StringCryptoTypeHandler.class);
                    PreparedStatement statement = mock(PreparedStatement.class);

                    writer.setNonNullParameter(statement, 1, "persisted-secret", null);

                    org.mockito.ArgumentCaptor<String> ciphertext = org.mockito.ArgumentCaptor.forClass(String.class);
                    verify(statement).setString(eq(1), ciphertext.capture());
                    persistedValue.set(ciphertext.getValue());
                });

        runner.withPropertyValues(
                        "mimir.boot.mybatis.crypto-enabled=true",
                        "mimir.boot.mybatis.crypto-key=" + key)
                .run(secondContext -> {
                    StringCryptoTypeHandler reader = secondContext.getBean(StringCryptoTypeHandler.class);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getString("secret")).thenReturn(persistedValue.get());

                    assertEquals("persisted-secret", reader.getNullableResult(resultSet, "secret"));
                });
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

    @Configuration(proxyBeanMethods = false)
    static class CustomCryptoKeyProviderConfiguration {

        @Bean
        CryptoKeyProvider customCryptoKeyProvider() {
            return () -> CUSTOM_CRYPTO_KEY;
        }
    }
}
