package com.yggdrasil.labs.mybatis.config;

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.yggdrasil.labs.mybatis.util.MapperPackageDetector;
import com.yggdrasil.labs.mybatis.util.ReflectionUtils;
import org.apache.ibatis.logging.stdout.StdOutImpl;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.util.CollectionUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
        InnerInterceptor pagination = ReflectionUtils.createPaginationInnerInterceptor();
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
        // 使用 Properties 中的方法获取最终扫描包（包含默认包和自动检测的包）
        String basePackages = getFinalMapperPackagesWithAutoDetection(properties);
        configurer.setBasePackage(basePackages);
        return configurer;
    }

    /**
     * 获取最终的 Mapper 扫描包列表，包含：
     * 1. 默认包：com.yggdrasil.labs.**.mapper
     * 2. 用户配置的包
     * 3. 自动检测的 processor 生成的 mapper 包
     *
     * @param properties MyBatis 配置属性
     * @return 最终地扫描包列表，用逗号分隔的字符串
     */
    private String getFinalMapperPackagesWithAutoDetection(MybatisProperties properties) {
        Set<String> packages = new LinkedHashSet<>();

        // 1. 始终包含默认包
        packages.add(MybatisProperties.DEFAULT_MAPPER_PACKAGE);

        // 2. 添加用户配置的包
        if (!CollectionUtils.isEmpty(properties.getMapperPackages())) {
            packages.addAll(properties.getMapperPackages());
        }

        // 3. 自动检测 processor 生成的 mapper 包
        Set<String> autoDetectedPackages = MapperPackageDetector.detectMapperPackages();
        packages.addAll(autoDetectedPackages);

        return String.join(",", packages);
    }


    @Bean
    public ConfigurationCustomizer mybatisConfigurationCustomizer(
            MybatisProperties properties, Environment env) {
        boolean isDevOrTest = env.acceptsProfiles(Profiles.of(
                MybatisConstants.PROFILE_DEV, MybatisConstants.PROFILE_TEST));
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
}


