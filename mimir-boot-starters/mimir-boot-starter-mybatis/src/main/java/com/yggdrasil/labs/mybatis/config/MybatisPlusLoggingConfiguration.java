package com.yggdrasil.labs.mybatis.config;

import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.yggdrasil.labs.mybatis.log.JsonSqlLogInnerInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * SQL 日志拦截器装配：开发、测试环境默认开启 JSON SQL 日志。
 */
@AutoConfiguration
public class MybatisPlusLoggingConfiguration {

    @Bean
    @ConditionalOnProperty(
            name = "mimir.mybatis.enable-json-sql-log",
            havingValue = "true",
            matchIfMissing = true
    )
    public InnerInterceptor jsonSqlLogInnerInterceptor(MybatisProperties properties, Environment env) {
        boolean isDevOrTest = env.acceptsProfiles(Profiles.of("dev", "test"));
        Boolean enableJson = properties.getEnableJsonSqlLog();
        if (enableJson == null) {
            enableJson = isDevOrTest;
        }
        if (enableJson) {
            return new JsonSqlLogInnerInterceptor();
        }
        return null;
    }
}


