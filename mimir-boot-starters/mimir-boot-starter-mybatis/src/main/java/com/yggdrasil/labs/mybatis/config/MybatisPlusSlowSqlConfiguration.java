package com.yggdrasil.labs.mybatis.config;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.yggdrasil.labs.mybatis.log.SlowSqlInterceptor;

/** 慢 SQL 拦截器自动配置。 */
@AutoConfiguration
@AutoConfigureBefore(name = "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration")
@ConditionalOnClass(StatementHandler.class)
@EnableConfigurationProperties(SlowSqlProperties.class)
public class MybatisPlusSlowSqlConfiguration {
    @Bean
    @ConditionalOnMissingBean(SlowSqlInterceptor.class)
    @ConditionalOnProperty(
            prefix = "mimir.boot.mybatis.slow-sql",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public SlowSqlInterceptor slowSqlInterceptor(SlowSqlProperties properties) {
        return new SlowSqlInterceptor(properties.getThresholdMs());
    }
}
