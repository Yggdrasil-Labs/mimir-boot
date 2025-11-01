package com.yggdrasil.labs.nacos.config;

import com.yggdrasil.labs.nacos.decrypt.ConfigDecryptProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Nacos 配置加密自动配置
 *
 * <p>功能说明：</p>
 * <ul>
 * <li>自动处理 Nacos 配置中的加密值</li>
 * <li>支持 ENC(encrypted_value) 格式的配置解密</li>
 * <li>在应用启动早期阶段进行配置解密</li>
 * <li>支持配置动态刷新时的解密</li>
 * </ul>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(name = "com.alibaba.cloud.nacos.NacosConfigProperties")
@ConditionalOnProperty(
        prefix = "mimir.nacos.encrypt",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
@EnableConfigurationProperties(NacosEncryptProperties.class)
public class NacosEncryptAutoConfiguration implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(NacosEncryptAutoConfiguration.class);

    private final NacosEncryptProperties properties;
    private ApplicationContext applicationContext;

    public NacosEncryptAutoConfiguration(NacosEncryptProperties properties) {
        this.properties = properties;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();
        processDecrypt(environment);
    }

    /**
     * 监听配置刷新事件，在配置动态刷新时重新解密
     *
     * @param event 环境变更事件
     */
    @EventListener
    public void onEnvironmentChange(EnvironmentChangeEvent event) {
        if (applicationContext != null && applicationContext.getEnvironment() instanceof ConfigurableEnvironment environment) {
            log.debug("检测到配置变更，重新处理配置解密，变更的配置键: {}", event.getKeys());
            processDecrypt(environment);
        }
    }

    /**
     * 处理配置解密
     *
     * @param environment Spring 环境配置
     */
    void processDecrypt(ConfigurableEnvironment environment) {
        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            log.debug("Nacos 配置加密脱敏功能已禁用");
            return;
        }

        log.debug("开始处理 Nacos 配置解密");
        ConfigDecryptProcessor processor = new ConfigDecryptProcessor(properties);
        processor.process(environment);
    }
}
