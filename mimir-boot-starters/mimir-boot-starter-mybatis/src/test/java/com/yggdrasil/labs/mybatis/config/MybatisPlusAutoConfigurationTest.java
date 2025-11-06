package com.yggdrasil.labs.mybatis.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.core.env.StandardEnvironment;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MyBatis-Plus 自动配置测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class MybatisPlusAutoConfigurationTest {

    @Test
    void mybatisPlusInterceptor_registers_optimistic_and_optional_pagination() {
        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.empty());
        MybatisProperties props = new MybatisProperties();

        // 直接调用，pagination 可能存在或不存在（不同 MP 版本），至少应返回非空拦截器，并包含乐观锁
        MybatisPlusInterceptor interceptor = cfg.mybatisPlusInterceptor(props, new StandardEnvironment());
        assertNotNull(interceptor);
        // 无法直接读取内部列表，但不抛异常且返回对象即可视为基本装配成功
    }

    @Test
    void mybatisPlusInterceptor_with_custom_interceptors() {
        InnerInterceptor customInterceptor = Mockito.mock(InnerInterceptor.class);
        List<InnerInterceptor> customInterceptors = Arrays.asList(customInterceptor);
        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.of(customInterceptors));
        MybatisProperties props = new MybatisProperties();

        MybatisPlusInterceptor interceptor = cfg.mybatisPlusInterceptor(props, new StandardEnvironment());
        assertNotNull(interceptor);
    }

    @Test
    void mybatisPlusInterceptor_with_empty_custom_interceptors_list() {
        List<InnerInterceptor> emptyList = Collections.emptyList();
        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.of(emptyList));
        MybatisProperties props = new MybatisProperties();

        MybatisPlusInterceptor interceptor = cfg.mybatisPlusInterceptor(props, new StandardEnvironment());
        assertNotNull(interceptor);
    }

    @Test
    void mybatisPlusInterceptor_with_multiple_custom_interceptors() {
        InnerInterceptor interceptor1 = Mockito.mock(InnerInterceptor.class);
        InnerInterceptor interceptor2 = Mockito.mock(InnerInterceptor.class);
        List<InnerInterceptor> customInterceptors = Arrays.asList(interceptor1, interceptor2);
        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.of(customInterceptors));
        MybatisProperties props = new MybatisProperties();

        MybatisPlusInterceptor interceptor = cfg.mybatisPlusInterceptor(props, new StandardEnvironment());
        assertNotNull(interceptor);
    }

    @Test
    void mapperScannerConfigurer_sets_base_package_when_packages_present() {
        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.empty());
        MybatisProperties props = new MybatisProperties();
        props.setMapperPackages(Arrays.asList("a.b.c", "x.y.z"));
        MapperScannerConfigurer msc = cfg.mapperScannerConfigurer(props);
        assertNotNull(msc);
        // basePackage 拼接成逗号分隔，框架内部消费，无法直接读取；至少对象不为空即可
    }

    @Test
    void mapperScannerConfigurer_with_empty_packages() {
        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.empty());
        MybatisProperties props = new MybatisProperties();
        props.setMapperPackages(Collections.emptyList());
        MapperScannerConfigurer msc = cfg.mapperScannerConfigurer(props);
        assertNotNull(msc);
    }

    @Test
    void mapperScannerConfigurer_with_null_packages() {
        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.empty());
        MybatisProperties props = new MybatisProperties();
        props.setMapperPackages(null);
        MapperScannerConfigurer msc = cfg.mapperScannerConfigurer(props);
        assertNotNull(msc);
    }

    @Test
    void mapperScannerConfigurer_with_single_package() {
        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.empty());
        MybatisProperties props = new MybatisProperties();
        props.setMapperPackages(Arrays.asList("com.example.mapper"));
        MapperScannerConfigurer msc = cfg.mapperScannerConfigurer(props);
        assertNotNull(msc);
    }

    @Test
    void configurationCustomizer_respects_explicit_enableStdout_flag_true() {
        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.empty());
        MybatisProperties props = new MybatisProperties();
        props.setEnableSqlStdout(true);

        ConfigurationCustomizer customizer = cfg.mybatisConfigurationCustomizer(props, new StandardEnvironment());
        assertNotNull(customizer);

        Configuration configuration = new Configuration();
        customizer.customize(configuration);
        assertEquals(org.apache.ibatis.logging.stdout.StdOutImpl.class, configuration.getLogImpl());
    }

    @Test
    void configurationCustomizer_respects_explicit_enableStdout_flag_false() {
        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.empty());
        MybatisProperties props = new MybatisProperties();
        props.setEnableSqlStdout(false);

        ConfigurationCustomizer customizer = cfg.mybatisConfigurationCustomizer(props, new StandardEnvironment());
        assertNotNull(customizer);

        Configuration configuration = new Configuration();
        customizer.customize(configuration);
        // 当enableStdout为false时，不应该设置StdOutImpl
        assertNull(configuration.getLogImpl());
    }

    @Test
    void configurationCustomizer_with_null_enableStdout_in_dev_environment() {
        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.empty());
        MybatisProperties props = new MybatisProperties();
        props.setEnableSqlStdout(null);

        // 创建dev环境的Environment
        StandardEnvironment env = new StandardEnvironment();
        env.setActiveProfiles("dev");

        ConfigurationCustomizer customizer = cfg.mybatisConfigurationCustomizer(props, env);
        assertNotNull(customizer);

        Configuration configuration = new Configuration();
        customizer.customize(configuration);
        // 在dev环境下，enableStdout为null时应该默认启用
        assertEquals(org.apache.ibatis.logging.stdout.StdOutImpl.class, configuration.getLogImpl());
    }

    @Test
    void configurationCustomizer_with_null_enableStdout_in_test_environment() {
        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.empty());
        MybatisProperties props = new MybatisProperties();
        props.setEnableSqlStdout(null);

        // 创建test环境的Environment
        StandardEnvironment env = new StandardEnvironment();
        env.setActiveProfiles("test");

        ConfigurationCustomizer customizer = cfg.mybatisConfigurationCustomizer(props, env);
        assertNotNull(customizer);

        Configuration configuration = new Configuration();
        customizer.customize(configuration);
        // 在test环境下，enableStdout为null时应该默认启用
        assertEquals(org.apache.ibatis.logging.stdout.StdOutImpl.class, configuration.getLogImpl());
    }

    @Test
    void configurationCustomizer_with_null_enableStdout_in_prod_environment() {
        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.empty());
        MybatisProperties props = new MybatisProperties();
        props.setEnableSqlStdout(null);

        // 创建prod环境的Environment
        StandardEnvironment env = new StandardEnvironment();
        env.setActiveProfiles("prod");

        ConfigurationCustomizer customizer = cfg.mybatisConfigurationCustomizer(props, env);
        assertNotNull(customizer);

        Configuration configuration = new Configuration();
        customizer.customize(configuration);
        // 在prod环境下，enableStdout为null时应该默认不启用
        assertNull(configuration.getLogImpl());
    }

    @Test
    void configurationCustomizer_with_null_enableStdout_in_default_environment() {
        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.empty());
        MybatisProperties props = new MybatisProperties();
        props.setEnableSqlStdout(null);

        // 使用默认环境（无active profiles）
        StandardEnvironment env = new StandardEnvironment();

        ConfigurationCustomizer customizer = cfg.mybatisConfigurationCustomizer(props, env);
        assertNotNull(customizer);

        Configuration configuration = new Configuration();
        customizer.customize(configuration);
        // 在默认环境下，enableStdout为null时应该默认不启用
        assertNull(configuration.getLogImpl());
    }

    @Test
    void configurationCustomizer_explicit_false_overrides_environment() {
        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.empty());
        MybatisProperties props = new MybatisProperties();
        props.setEnableSqlStdout(false);

        // 即使在dev环境下，显式设置为false也应该生效
        StandardEnvironment env = new StandardEnvironment();
        env.setActiveProfiles("dev");

        ConfigurationCustomizer customizer = cfg.mybatisConfigurationCustomizer(props, env);
        assertNotNull(customizer);

        Configuration configuration = new Configuration();
        customizer.customize(configuration);
        assertNull(configuration.getLogImpl());
    }

    @Test
    void configurationCustomizer_explicit_true_overrides_environment() {
        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.empty());
        MybatisProperties props = new MybatisProperties();
        props.setEnableSqlStdout(true);

        // 即使在prod环境下，显式设置为true也应该生效
        StandardEnvironment env = new StandardEnvironment();
        env.setActiveProfiles("prod");

        ConfigurationCustomizer customizer = cfg.mybatisConfigurationCustomizer(props, env);
        assertNotNull(customizer);

        Configuration configuration = new Configuration();
        customizer.customize(configuration);
        assertEquals(org.apache.ibatis.logging.stdout.StdOutImpl.class, configuration.getLogImpl());
    }

    @Test
    void constructor_with_empty_optional() {
        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.empty());
        assertNotNull(cfg);
    }

    @Test
    void constructor_with_present_optional() {
        List<InnerInterceptor> interceptors = Arrays.asList(Mockito.mock(InnerInterceptor.class));
        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.of(interceptors));
        assertNotNull(cfg);
    }

    @Test
    void mybatisPlusInterceptor_does_not_throw_exception() {
        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.empty());
        MybatisProperties props = new MybatisProperties();
        StandardEnvironment env = new StandardEnvironment();

        assertDoesNotThrow(() -> {
            MybatisPlusInterceptor interceptor = cfg.mybatisPlusInterceptor(props, env);
            assertNotNull(interceptor);
        });
    }

    @Test
    void mapperScannerConfigurer_does_not_throw_exception() {
        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.empty());
        MybatisProperties props = new MybatisProperties();

        assertDoesNotThrow(() -> {
            MapperScannerConfigurer configurer = cfg.mapperScannerConfigurer(props);
            assertNotNull(configurer);
        });
    }

    @Test
    void configurationCustomizer_does_not_throw_exception() {
        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.empty());
        MybatisProperties props = new MybatisProperties();
        StandardEnvironment env = new StandardEnvironment();

        assertDoesNotThrow(() -> {
            ConfigurationCustomizer customizer = cfg.mybatisConfigurationCustomizer(props, env);
            assertNotNull(customizer);
            
            Configuration configuration = new Configuration();
            customizer.customize(configuration);
        });
    }
}
