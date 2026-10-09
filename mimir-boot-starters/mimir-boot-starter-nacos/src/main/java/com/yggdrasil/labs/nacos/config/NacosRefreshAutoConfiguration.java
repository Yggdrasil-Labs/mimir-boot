package com.yggdrasil.labs.nacos.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration.RefreshProperties;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.cloud.context.scope.refresh.RefreshScope;
import org.springframework.cloud.util.ConditionalOnBootstrapDisabled;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

import com.alibaba.cloud.nacos.configdata.NacosConfigDataLocationResolver;

/** 为默认 ConfigData 刷新链补充 Nacos 属性源删除与解密差异同步。 */
@AutoConfiguration(before = RefreshAutoConfiguration.class)
@ConditionalOnClass(NacosConfigDataLocationResolver.class)
@ConditionalOnBootstrapDisabled
@ConditionalOnProperty(
        name = "spring.cloud.refresh.enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(RefreshProperties.class)
public class NacosRefreshAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ContextRefresher.class)
    public NacosConfigDataContextRefresher nacosConfigDataContextRefresher(
            ConfigurableApplicationContext context,
            RefreshScope scope,
            RefreshProperties properties) {
        return new NacosConfigDataContextRefresher(context, scope, properties);
    }
}
