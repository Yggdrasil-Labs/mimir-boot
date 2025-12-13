package com.yggdrasil.labs.mybatis.config;

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.yggdrasil.labs.mybatis.util.MapperPackageDetector;
import com.yggdrasil.labs.mybatis.util.ReflectionUtils;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.logging.stdout.StdOutImpl;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * MyBatis-Plus 自动配置，注册常用拦截器。
 * <p>
 * 分页拦截器：从 MyBatis-Plus 3.5.9 开始，分页插件需要单独引入 mybatis-plus-jsqlparser 依赖。
 * 本 Starter 已自动包含该依赖，使用 @ConditionalOnClass 确保类存在时才创建 Bean。
 * <p>
 * 注意：此自动配置类需要在 MyBatis-Plus 的自动配置之前加载，以确保 MapperScannerConfigurer
 * 能够被正确创建。如果 MyBatis-Plus 的自动配置先创建了 MapperScannerConfigurer，
 * 此配置类中的 mapperScannerConfigurer 方法将不会执行（因为 @ConditionalOnMissingBean）。
 */
@AutoConfiguration
@AutoConfigureBefore(name = "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration")
@ConditionalOnClass({MybatisPlusInterceptor.class, MapperScannerConfigurer.class})
@EnableConfigurationProperties(MybatisProperties.class)
public class MybatisPlusAutoConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(MybatisPlusAutoConfiguration.class);

    @Bean
    @ConditionalOnClass(name = "com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor")
    public MybatisPlusInterceptor mybatisPlusInterceptor(
            MybatisProperties properties, Environment env, ListableBeanFactory beanFactory) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        
        // 分页拦截器（使用反射加载，避免编译时依赖）
        // 使用 @ConditionalOnClass 确保类存在时才创建 Bean
        // 注意：即使有 @ConditionalOnClass，在类加载时仍可能失败，所以使用反射更安全
        InnerInterceptor pagination = ReflectionUtils.createPaginationInnerInterceptor();
        if (pagination != null) {
            interceptor.addInnerInterceptor(pagination);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("分页拦截器已添加到 MybatisPlusInterceptor");
            }
        } else {
            // 如果 @ConditionalOnClass 生效，这里应该不会执行
            // 但如果类加载失败，这里会记录警告
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("分页拦截器未找到，请确认已引入 mybatis-plus-jsqlparser 依赖（MyBatis-Plus 3.5.9+ 需要）");
            }
        }
        
        // 乐观锁
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        
        // 从 Spring 容器中获取所有 InnerInterceptor Bean（包括 JsonSqlLogInnerInterceptor 等）
        // 这样可以确保即使配置类加载顺序不同，也能正确获取到所有拦截器
        try {
            String[] beanNames = beanFactory.getBeanNamesForType(InnerInterceptor.class, false, false);
            if (beanNames.length > 0) {
                for (String beanName : beanNames) {
                    InnerInterceptor inner = beanFactory.getBean(beanName, InnerInterceptor.class);
                    interceptor.addInnerInterceptor(inner);
                    if (LOGGER.isInfoEnabled()) {
                        LOGGER.info("已添加自定义拦截器: {} (bean: {})", inner.getClass().getSimpleName(), beanName);
                    }
                }
            } else {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("没有找到自定义 InnerInterceptor Bean");
                }
            }
        } catch (Exception e) {
            // 如果获取 Bean 时出错，记录警告但不影响主流程
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("获取 InnerInterceptor Bean 时发生异常，跳过自定义拦截器", e);
            }
        }
        
        return interceptor;
    }

    @Bean
    @ConditionalOnMissingBean(MapperScannerConfigurer.class)
    public MapperScannerConfigurer mapperScannerConfigurer(MybatisProperties properties) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("创建 MapperScannerConfigurer Bean，开始配置 Mapper 扫描包");
        }
        
        MapperScannerConfigurer configurer = new MapperScannerConfigurer();
        // 使用 Properties 中的方法获取最终扫描包（包含默认包和自动检测的包）
        String basePackages = getFinalMapperPackagesWithAutoDetection(properties);
        configurer.setBasePackage(basePackages);
        
        // 设置 annotationClass 为 Mapper，只扫描带有 @Mapper 注解的接口
        // 这样可以避免与 Spring 组件扫描冲突，防止重复注册
        configurer.setAnnotationClass(Mapper.class);
        
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("MapperScannerConfigurer 已配置，扫描包: {}，注解类型: @Mapper", basePackages);
        }
        
        return configurer;
    }

    /**
     * 获取最终的 Mapper 扫描包列表，包含：
     * 1. 默认包：com.yggdrasil.labs.**.mapper
     * 2. 用户配置的包
     * 3. 自动检测的 mapper 包（包括 processor 生成的 mapper 包）
     * 
     * 注意：自动检测会扫描所有以 ".mapper" 结尾的包，包括 com.yggdrasil.labs 下的包。
     * 即使默认包使用了通配符，自动检测也会添加具体的包路径，确保所有 mapper 都能被正确扫描到。
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


