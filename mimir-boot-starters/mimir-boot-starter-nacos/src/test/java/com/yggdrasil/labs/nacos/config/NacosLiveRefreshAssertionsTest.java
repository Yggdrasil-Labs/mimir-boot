package com.yggdrasil.labs.nacos.config;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidFailures")
    void shouldRejectFailuresWithoutCurrentExpectedAuthenticationEvidence(
            String scenario, int previousFailures, List<RuntimeException> failures) {
        NacosLiveRefreshIT.Lifecycle lifecycle = new NacosLiveRefreshIT.Lifecycle();
        failures.forEach(failure -> recordFailure(lifecycle, failure));

        assertThrows(
                AssertionError.class,
                () ->
                        NacosLiveRefreshIT.assertCurrentDecryptionFailure(
                                lifecycle, previousFailures, EXPECTED_KEYS));
    }

    private static Stream<Arguments> invalidFailures() {
        return Stream.of(
                Arguments.of("远程加载失败", 0, List.of(new IllegalStateException("远程加载失败"))),
                Arguments.of("无关事件异常", 0, List.of(new IllegalArgumentException("无关事件监听器失败"))),
                Arguments.of("其他属性认证失败", 0, List.of(authenticationFailure("unrelated.secret"))),
                Arguments.of("仅有历史认证失败", 1, List.of(authenticationFailure("live.required.secret"))),
                Arguments.of(
                        "历史认证失败不能解释本轮加载失败",
                        1,
                        List.of(
                                authenticationFailure("live.required.secret"),
                                new IllegalStateException("本轮远程加载失败"))),
                Arguments.of(
                        "损坏密文不能作为错误密钥证据",
                        0,
                        List.of(
                                decryptionFailure(
                                        "live.required.secret",
                                        "ENC(损坏密文)",
                                        ConfigCryptoUtils.generateKey()))),
                Arguments.of(
                        "本轮认证失败夹杂无关异常",
                        0,
                        List.of(
                                authenticationFailure("live.required.secret"),
                                new IllegalStateException("本轮额外监听器异常"))));
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
