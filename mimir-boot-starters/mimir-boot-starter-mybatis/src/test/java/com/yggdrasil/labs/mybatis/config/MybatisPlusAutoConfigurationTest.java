package com.yggdrasil.labs.mybatis.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MyBatis-Plus 自动配置测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class MybatisPlusAutoConfigurationTest {

    private MybatisPlusAutoConfiguration configuration;
    private MybatisProperties properties;
    private Environment environment;

    @BeforeEach
    void setUp() {
        configuration = new MybatisPlusAutoConfiguration();
        properties = new MybatisProperties();
        environment = new StandardEnvironment();
    }

    @Test
    void testMybatisPlusInterceptorCreation() {
        MybatisPlusInterceptor interceptor = configuration.mybatisPlusInterceptor(properties, environment);

        assertNotNull(interceptor);
        // 验证拦截器已注册（至少包含乐观锁拦截器）
        assertTrue(interceptor.getInterceptors().size() > 0);
    }

    @Test
    void testMybatisPlusInterceptorWithProperties() {
        properties.setEnableSqlStdout(true);
        properties.setEnableJsonSqlLog(false);

        MybatisPlusInterceptor interceptor = configuration.mybatisPlusInterceptor(properties, environment);
        assertNotNull(interceptor);
    }

    @Test
    void testConfigurationCustomizer() {
        properties.setEnableSqlStdout(true);
        var customizer = configuration.mybatisConfigurationCustomizer(properties, environment);

        assertNotNull(customizer);
    }

    @Test
    void testConfigurationCustomizerWithNullEnableStdout() {
        properties.setEnableSqlStdout(null);
        var customizer = configuration.mybatisConfigurationCustomizer(properties, environment);

        assertNotNull(customizer);
    }

    @Test
    void testMapperScannerConfigurerWithEmptyPackages() {
        properties.setMapperPackages(Collections.emptyList());
        var configurer = configuration.mapperScannerConfigurer(properties);

        assertNotNull(configurer);
    }

    @Test
    void testMapperScannerConfigurerWithPackages() throws Exception {
        properties.setMapperPackages(Arrays.asList("com.example.mapper", "com.example.other"));
        var configurer = configuration.mapperScannerConfigurer(properties);

        assertNotNull(configurer);
        // 使用反射验证 basePackage 字段已正确设置
        // MapperScannerConfigurer 可能没有公开的 getter 方法
        java.lang.reflect.Field field = configurer.getClass().getDeclaredField("basePackage");
        field.setAccessible(true);
        String basePackage = (String) field.get(configurer);
        assertEquals("com.example.mapper,com.example.other", basePackage);
    }
}

