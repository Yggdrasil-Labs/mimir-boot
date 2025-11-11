package com.yggdrasil.labs.mybatis.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import org.apache.ibatis.logging.stdout.StdOutImpl;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import java.util.List;
import java.util.Optional;

/**
 * MyBatis-Plus 自动配置，注册常用拦截器。
 * <p>
 * 为了兼容不同版本的 MyBatis-Plus，分页拦截器通过反射可选加载：
 * - 首选 com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor
 */
@AutoConfiguration
@EnableConfigurationProperties(MybatisProperties.class)
public class MybatisPlusAutoConfiguration {

    private final Optional<List<InnerInterceptor>> innerInterceptors;

    /**
     * 构造器注入
     *
     * @param innerInterceptors 自定义拦截器列表（可选，如果不存在则注入 Optional.empty()）
     */
    public MybatisPlusAutoConfiguration(Optional<List<InnerInterceptor>> innerInterceptors) {
        this.innerInterceptors = innerInterceptors;
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(
            MybatisProperties properties, Environment env) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 可选分页拦截器
        InnerInterceptor pagination = tryCreatePaginationInnerInterceptor();
        if (pagination != null) {
            interceptor.addInnerInterceptor(pagination);
        }
        // 乐观锁
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        // 装配外部或其他配置类提供的自定义拦截器
        innerInterceptors.ifPresent(interceptors -> {
            if (!interceptors.isEmpty()) {
                interceptors.forEach(interceptor::addInnerInterceptor);
            }
        });
        return interceptor;
    }

    @Bean
    @ConditionalOnMissingBean(MapperScannerConfigurer.class)
    public MapperScannerConfigurer mapperScannerConfigurer(MybatisProperties properties) {
        MapperScannerConfigurer configurer = new MapperScannerConfigurer();
        // 使用 Properties 中的方法获取最终扫描包（包含默认包）
        configurer.setBasePackage(properties.getFinalMapperPackages());
        return configurer;
    }

    @Bean
    public ConfigurationCustomizer mybatisConfigurationCustomizer(
            MybatisProperties properties, Environment env) {
        boolean isDevOrTest = env.acceptsProfiles(Profiles.of("dev", "test"));
        return configuration -> {
            Boolean enableStdout = properties.getEnableSqlStdout();
            if (enableStdout == null) {
                enableStdout = isDevOrTest;
            }
            if (enableStdout) {
                configuration.setLogImpl(StdOutImpl.class);
            }
        };
    }

    private InnerInterceptor tryCreatePaginationInnerInterceptor() {
        try {
            Class<?> clazz = Class.forName("com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor");
            Object instance = clazz.getDeclaredConstructor().newInstance();
            return (InnerInterceptor) instance;
        } catch (Exception ignore) {
            return null;
        }
    }
}


