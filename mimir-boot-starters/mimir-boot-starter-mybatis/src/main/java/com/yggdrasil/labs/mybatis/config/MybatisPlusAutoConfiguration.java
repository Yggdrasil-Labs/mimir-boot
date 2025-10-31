package com.yggdrasil.labs.mybatis.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import org.apache.ibatis.logging.stdout.StdOutImpl;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * MyBatis-Plus 自动配置，注册常用拦截器。
 * <p>
 * 为了兼容不同版本的 MyBatis-Plus，分页拦截器通过反射可选加载：
 * - 首选 com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor
 */
@AutoConfiguration
@EnableConfigurationProperties(MybatisProperties.class)
public class MybatisPlusAutoConfiguration {

    @Autowired(required = false)
    private List<InnerInterceptor> innerInterceptors;

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
        if (innerInterceptors != null) {
            innerInterceptors.forEach(interceptor::addInnerInterceptor);
        }
        return interceptor;
    }

    @Bean
    @ConditionalOnMissingBean(MapperScannerConfigurer.class)
    public MapperScannerConfigurer mapperScannerConfigurer(MybatisProperties properties) {
        MapperScannerConfigurer configurer = new MapperScannerConfigurer();
        if (!CollectionUtils.isEmpty(properties.getMapperPackages())) {
            configurer.setBasePackage(String.join(",", properties.getMapperPackages()));
        }
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
        } catch (Throwable ignore) {
            return null;
        }
    }
}


