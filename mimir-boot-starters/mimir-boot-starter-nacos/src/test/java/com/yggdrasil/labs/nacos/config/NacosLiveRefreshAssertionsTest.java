package com.yggdrasil.labs.nacos.config;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import com.yggdrasil.labs.nacos.crypto.ConfigCryptoUtils;
import com.yggdrasil.labs.nacos.decrypt.ConfigDecryptProcessor;

/** 真实服务验收的失败观察必须区分本轮认证失败和其他事件异常。 */
class NacosLiveRefreshAssertionsTest {

    private static final Set<String> EXPECTED_KEYS =
            Set.of("live.required.secret", "live.fallback.secret");

    @Test
    void shouldAcceptCurrentAuthenticationFailureWithoutReplacingOriginalException() {
        NacosLiveRefreshIT.Lifecycle lifecycle = new NacosLiveRefreshIT.Lifecycle();
        IllegalStateException failure = authenticationFailure("live.required.secret");
        recordFailure(lifecycle, failure);
        recordFailure(lifecycle, failure);

        NacosLiveRefreshIT.assertCurrentDecryptionFailure(lifecycle, 0, EXPECTED_KEYS);
    }

    @Test
    void shouldAcceptRepeatedCurrentAuthenticationFailures() {
        NacosLiveRefreshIT.Lifecycle lifecycle = new NacosLiveRefreshIT.Lifecycle();
        recordFailure(lifecycle, authenticationFailure("live.required.secret"));
        recordFailure(lifecycle, authenticationFailure("live.fallback.secret"));

        NacosLiveRefreshIT.assertCurrentDecryptionFailure(lifecycle, 0, EXPECTED_KEYS);
    }

    @Test
    void shouldRejectRemoteLoadFailureAsDecryptionEvidence() {
        NacosLiveRefreshIT.Lifecycle lifecycle = new NacosLiveRefreshIT.Lifecycle();
        recordFailure(lifecycle, new IllegalStateException("远程加载失败"));

        assertThrows(
                AssertionError.class,
                () ->
                        NacosLiveRefreshIT.assertCurrentDecryptionFailure(
                                lifecycle, 0, EXPECTED_KEYS));
    }

    @Test
    void shouldRejectUnrelatedEventFailure() {
        NacosLiveRefreshIT.Lifecycle lifecycle = new NacosLiveRefreshIT.Lifecycle();
        recordFailure(lifecycle, new IllegalArgumentException("无关事件监听器失败"));

        assertThrows(
                AssertionError.class,
                () ->
                        NacosLiveRefreshIT.assertCurrentDecryptionFailure(
                                lifecycle, 0, EXPECTED_KEYS));
    }

    @Test
    void shouldRejectAuthenticationFailureForAnotherProperty() {
        NacosLiveRefreshIT.Lifecycle lifecycle = new NacosLiveRefreshIT.Lifecycle();
        recordFailure(lifecycle, authenticationFailure("unrelated.secret"));

        assertThrows(
                AssertionError.class,
                () ->
                        NacosLiveRefreshIT.assertCurrentDecryptionFailure(
                                lifecycle, 0, EXPECTED_KEYS));
    }

    @Test
    void shouldRejectHistoricalAuthenticationFailureWithoutCurrentFailure() {
        NacosLiveRefreshIT.Lifecycle lifecycle = new NacosLiveRefreshIT.Lifecycle();
        recordFailure(lifecycle, authenticationFailure("live.required.secret"));

        assertThrows(
                AssertionError.class,
                () ->
                        NacosLiveRefreshIT.assertCurrentDecryptionFailure(
                                lifecycle, 1, EXPECTED_KEYS));
    }

    @Test
    void shouldNotUseHistoricalAuthenticationFailureToExplainCurrentLoadFailure() {
        NacosLiveRefreshIT.Lifecycle lifecycle = new NacosLiveRefreshIT.Lifecycle();
        recordFailure(lifecycle, authenticationFailure("live.required.secret"));
        recordFailure(lifecycle, new IllegalStateException("本轮远程加载失败"));

        assertThrows(
                AssertionError.class,
                () ->
                        NacosLiveRefreshIT.assertCurrentDecryptionFailure(
                                lifecycle, 1, EXPECTED_KEYS));
    }

    @Test
    void shouldRejectMalformedCiphertextAsWrongKeyAuthenticationEvidence() {
        NacosLiveRefreshIT.Lifecycle lifecycle = new NacosLiveRefreshIT.Lifecycle();
        recordFailure(
                lifecycle,
                decryptionFailure(
                        "live.required.secret", "ENC(损坏密文)", ConfigCryptoUtils.generateKey()));

        assertThrows(
                AssertionError.class,
                () ->
                        NacosLiveRefreshIT.assertCurrentDecryptionFailure(
                                lifecycle, 0, EXPECTED_KEYS));
    }

    @Test
    void shouldRejectUnrelatedFailureAlongsideCurrentAuthenticationFailure() {
        NacosLiveRefreshIT.Lifecycle lifecycle = new NacosLiveRefreshIT.Lifecycle();
        recordFailure(lifecycle, authenticationFailure("live.required.secret"));
        recordFailure(lifecycle, new IllegalStateException("本轮额外监听器异常"));

        assertThrows(
                AssertionError.class,
                () ->
                        NacosLiveRefreshIT.assertCurrentDecryptionFailure(
                                lifecycle, 0, EXPECTED_KEYS));
    }

    private static void recordFailure(
            NacosLiveRefreshIT.Lifecycle lifecycle, RuntimeException failure) {
        var multicaster =
                NacosLiveRefreshIT.ConsumerConfiguration.applicationEventMulticaster(
                        new DefaultListableBeanFactory(), lifecycle);
        multicaster.addApplicationListener(
                event -> {
                    throw failure;
                });
        RuntimeException propagated =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                multicaster.multicastEvent(
                                        new PayloadApplicationEvent<>(lifecycle, "验收失败观察")));
        assertSame(failure, propagated);
    }

    private static IllegalStateException authenticationFailure(String propertyName) {
        String encryptionKey = ConfigCryptoUtils.generateKey();
        String configuredKey = ConfigCryptoUtils.generateKey();
        return decryptionFailure(
                propertyName,
                "ENC(" + ConfigCryptoUtils.encrypt("本轮测试值", encryptionKey) + ")",
                configuredKey);
    }

    private static IllegalStateException decryptionFailure(
            String propertyName, String encrypted, String configuredKey) {
        NacosEncryptProperties properties = new NacosEncryptProperties();
        properties.setKey(configuredKey);
        StandardEnvironment environment = new StandardEnvironment();
        environment
                .getPropertySources()
                .addFirst(new MapPropertySource("验收错误配置", Map.of(propertyName, encrypted)));
        ConfigDecryptProcessor processor = new ConfigDecryptProcessor(properties);
        return assertThrows(IllegalStateException.class, () -> processor.process(environment));
    }
}
