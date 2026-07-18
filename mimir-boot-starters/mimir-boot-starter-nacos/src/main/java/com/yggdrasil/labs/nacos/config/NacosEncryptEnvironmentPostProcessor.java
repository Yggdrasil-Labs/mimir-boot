package com.yggdrasil.labs.nacos.config;

import com.yggdrasil.labs.nacos.decrypt.ConfigDecryptProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * 在 Spring ApplicationContext 刷新前解密 Nacos 配置。
 *
 * <p>Source: https://docs.spring.io/spring-boot/3.3/api/java/org/springframework/boot/env/EnvironmentPostProcessor.html</p>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
public class NacosEncryptEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        NacosEncryptProperties properties = Binder.get(environment)
                .bind("mimir.nacos.encrypt", NacosEncryptProperties.class)
                .orElseGet(NacosEncryptProperties::new);
        new ConfigDecryptProcessor(properties).process(environment);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
