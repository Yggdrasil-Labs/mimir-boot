package com.yggdrasil.labs.mybatis.config;

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.yggdrasil.labs.mybatis.util.ReflectionUtils;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.logging.slf4j.Slf4jImpl;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.Arrays;
import java.util.stream.Collectors;

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
    @ConditionalOnMissingBean(MybatisPlusInterceptor.class)
    @ConditionalOnClass(name = "com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor")
    public MybatisPlusInterceptor mybatisPlusInterceptor(
            MybatisProperties properties, ListableBeanFactory beanFactory) {
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
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("已添加自定义拦截器: {} (bean: {})", inner.getClass().getSimpleName(), beanName);
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
        // 对外保留通配符包模式；MyBatis 扫描器必须接收实际的基础包名。
        String packagePatterns = properties.getEffectiveMapperPackages();
        String basePackages = normalizeMapperScanPackages(packagePatterns);
        configurer.setBasePackage(basePackages);
        
        // 设置 annotationClass 为 Mapper，只扫描带有 @Mapper 注解的接口
        // 这样可以避免与 Spring 组件扫描冲突，防止重复注册
        configurer.setAnnotationClass(Mapper.class);
        
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("MapperScannerConfigurer 已配置，扫描包: {}，注解类型: @Mapper", basePackages);
        }
        
        return configurer;
    }


    private static String normalizeMapperScanPackages(String packagePatterns) {
        return Arrays.stream(packagePatterns.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(MybatisPlusAutoConfiguration::normalizeMapperScanPackage)
                .distinct()
                .collect(Collectors.joining(","));
    }

    private static String normalizeMapperScanPackage(String packagePattern) {
        if (packagePattern.endsWith(MybatisConstants.PACKAGE_WILDCARD_SUFFIX)) {
            return packagePattern.substring(0,
                    packagePattern.length() - MybatisConstants.PACKAGE_WILDCARD_SUFFIX.length());
        }
        return packagePattern;
    }

    /**
     * 配置 MyBatis 日志实现。
     * <p>
     * 强制使用 SLF4J 作为 MyBatis 的日志实现，确保日志统一管理。
     * <p>
     * 是否打印 SQL 由日志级别控制：
     * <ul>
     *   <li>在 logback-spring.xml 中配置 Mapper 接口的日志级别</li>
     *   <li>例如：{@code <logger name="com.example.mapper" level="DEBUG"/>} 会打印 SQL</li>
     *   <li>例如：{@code <logger name="com.example.mapper" level="INFO"/>} 不会打印 SQL</li>
     * </ul>
     * <p>
     * 这样确保在其他项目引入此 starter 时，MyBatis 永远使用 slf4j，
     * 并且可以通过日志框架统一控制是否打印 SQL。
     *
     * @return ConfigurationCustomizer
     */
    @Bean
    public ConfigurationCustomizer mybatisConfigurationCustomizer() {
        return configuration -> {
            // 永远使用 slf4j：显式设置 Slf4jImpl，确保一定是 slf4j
            // 这样即使类路径中有其他日志框架，也优先使用 slf4j
            try {
                configuration.setLogImpl(Slf4jImpl.class);
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("MyBatis 日志已配置为使用 slf4j，是否打印 SQL 由日志级别控制");
                }
            } catch (Exception e) {
                // 如果 Slf4jImpl 不可用（理论上不应该发生，因为 Spring Boot 默认包含 slf4j）
                // 则记录错误，但不回退到其他日志实现
                if (LOGGER.isErrorEnabled()) {
                    LOGGER.error("无法设置 MyBatis 使用 slf4j，请检查类路径中是否包含 slf4j 相关依赖", e);
                }
                throw new IllegalStateException("无法设置 MyBatis 使用 slf4j，请检查类路径中是否包含 slf4j 相关依赖", e);
            }
        };
    }
}
