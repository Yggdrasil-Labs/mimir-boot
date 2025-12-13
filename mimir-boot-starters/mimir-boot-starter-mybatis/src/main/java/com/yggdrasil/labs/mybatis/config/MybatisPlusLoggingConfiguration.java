package com.yggdrasil.labs.mybatis.config;

import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.yggdrasil.labs.mybatis.log.JsonSqlLogInnerInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * SQL 日志拦截器装配：开发、测试环境默认开启 JSON SQL 日志。
 * <p>
 * 注意：必须在 MybatisPlusAutoConfiguration 之前加载，
 * 这样 JsonSqlLogInnerInterceptor Bean 才能被注入到 MybatisPlusInterceptor 中。
 */
@AutoConfiguration
@AutoConfigureBefore(MybatisPlusAutoConfiguration.class)
@EnableConfigurationProperties(MybatisProperties.class)
public class MybatisPlusLoggingConfiguration {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(MybatisPlusLoggingConfiguration.class);

    private final MybatisProperties properties;
    private final Environment environment;

    public MybatisPlusLoggingConfiguration(MybatisProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    /**
     * 注册 JSON SQL 日志拦截器
     * <p>
     * 启用条件：
     * 1. 如果显式配置了 enable-json-sql-log，则使用配置值
     * 2. 如果未配置，则在 dev、local、test 环境自动启用
     * 3. 可通过 mimir.boot.mybatis.enable-json-sql-log=false 显式禁用
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "mimir.boot.mybatis",
            name = "enable-json-sql-log",
            havingValue = "true",
            matchIfMissing = true  // 未配置时默认创建 Bean
    )
    public InnerInterceptor jsonSqlLogInnerInterceptor() {
        // 判断是否真正启用
        Boolean enableJson = properties.getEnableJsonSqlLog();
        if (enableJson == null) {
            // 未配置时，只在 dev/local/test 环境启用
            boolean isDevOrTest = environment.acceptsProfiles(
                    Profiles.of("dev", "local", "development", "test")
            );
            if (!isDevOrTest) {
                // 生产环境且未显式配置，返回空拦截器（不记录日志）
                LOGGER.info("JSON SQL 日志已禁用（生产环境且未显式配置）");
                return new NoOpInnerInterceptor();
            }
        } else if (!enableJson) {
            // 显式禁用
            LOGGER.info("JSON SQL 日志已禁用（显式配置）");
            return new NoOpInnerInterceptor();
        }
        
        // 启用 JSON SQL 日志
        LOGGER.info("JSON SQL 日志已启用，将记录到 SQL.JSON logger");
        return new JsonSqlLogInnerInterceptor();
    }

    /**
     * 空操作拦截器，用于条件不满足时占位
     */
    private static class NoOpInnerInterceptor implements InnerInterceptor {
        // 不做任何操作
    }
}


