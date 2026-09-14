package com.yggdrasil.labs.nacos.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import com.yggdrasil.labs.nacos.decrypt.ConfigDecryptProcessor;

/**
 * 在 Spring ApplicationContext 刷新前解密 Nacos 配置。
 *
 * <p>Source:
 * https://docs.spring.io/spring-boot/3.3/api/java/org/springframework/boot/env/EnvironmentPostProcessor.html
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
public class NacosEncryptEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment environment, SpringApplication application) {
        if (!NacosEncryptPropertiesResolver.isAnyPrefixBound(environment)) {
            return;
        }
        NacosEncryptProperties properties =
                NacosEncryptPropertiesResolver.resolve(environment, new NacosEncryptProperties());
        new ConfigDecryptProcessor(properties).process(environment);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
