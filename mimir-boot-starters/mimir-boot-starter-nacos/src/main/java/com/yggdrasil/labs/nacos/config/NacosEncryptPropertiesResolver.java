package com.yggdrasil.labs.nacos.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * 解析 Nacos 加密配置，并在迁移期兼容旧前缀。
 *
 * @author Yggdrasil Labs
 * @since 2.1.1
 */
final class NacosEncryptPropertiesResolver {

    private static final Logger log = LoggerFactory.getLogger(NacosEncryptPropertiesResolver.class);

    private NacosEncryptPropertiesResolver() {
    }

    static NacosEncryptProperties resolve(
            ConfigurableEnvironment environment,
            NacosEncryptProperties fallbackProperties) {
        Binder binder = Binder.get(environment);
        BindResult<NacosEncryptProperties> current = binder.bind(NacosEncryptProperties.PREFIX,
                NacosEncryptProperties.class);
        BindResult<NacosEncryptProperties> legacy = binder.bind(NacosEncryptProperties.LEGACY_PREFIX,
                NacosEncryptProperties.class);

        if (current.isBound()) {
            if (legacy.isBound()) {
                log.warn("检测到已弃用的配置前缀 {}，已忽略并使用 {}",
                        NacosEncryptProperties.LEGACY_PREFIX, NacosEncryptProperties.PREFIX);
            }
            return current.get();
        }
        if (legacy.isBound()) {
            log.warn("配置前缀 {} 已弃用，请迁移到 {}",
                    NacosEncryptProperties.LEGACY_PREFIX, NacosEncryptProperties.PREFIX);
            return legacy.get();
        }
        return fallbackProperties;
    }
}
