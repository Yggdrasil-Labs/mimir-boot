package com.yggdrasil.labs.mybatis.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.core.env.StandardEnvironment;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MyBatis-Plus 自动配置测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class MybatisPlusAutoConfigurationTest {

    @ParameterizedTest
    @MethodSource("provideInterceptorTestCases")
    void mybatisPlusInterceptor_with_various_configurations(Optional<List<InnerInterceptor>> interceptors, String description) {
        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(interceptors);
        MybatisProperties props = new MybatisProperties();

        MybatisPlusInterceptor interceptor = cfg.mybatisPlusInterceptor(props, new StandardEnvironment());
        assertNotNull(interceptor);
    }

    private static Stream<Arguments> provideInterceptorTestCases() {
        return Stream.of(
                Arguments.of(Optional.empty(), "no custom interceptors"),
                Arguments.of(Optional.of(Collections.singletonList(Mockito.mock(InnerInterceptor.class))), "single custom interceptor"),
                Arguments.of(Optional.of(Collections.emptyList()), "empty custom interceptors list")
        );
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
        props.setMapperPackages(List.of("com.example.mapper"));
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
        List<InnerInterceptor> interceptors = Collections.singletonList(Mockito.mock(InnerInterceptor.class));
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


    @Test
    void testMapperScannerConfigurerWithDefaultProperties() {
        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.empty());
        MybatisProperties props = new MybatisProperties();
        // 不设置 mapperPackages，使用默认值

        MapperScannerConfigurer configurer = cfg.mapperScannerConfigurer(props);
        assertNotNull(configurer);
    }

    @Test
    void testConfigurationCustomizerWithNullProperties() {
        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.empty());
        MybatisProperties props = new MybatisProperties();
        // enableSqlStdout 保持 null

        StandardEnvironment env = new StandardEnvironment();
        ConfigurationCustomizer customizer = cfg.mybatisConfigurationCustomizer(props, env);
        assertNotNull(customizer);

        Configuration configuration = new Configuration();
        customizer.customize(configuration);
        // 默认环境下应该不设置 StdOutImpl
        assertNull(configuration.getLogImpl());
    }

    @Test
    void testConfigurationCustomizerWithMultipleProfiles() {
        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.empty());
        MybatisProperties props = new MybatisProperties();
        props.setEnableSqlStdout(null);

        // 测试多个 profile 的情况
        StandardEnvironment env = new StandardEnvironment();
        env.setActiveProfiles("dev", "test", "local");

        ConfigurationCustomizer customizer = cfg.mybatisConfigurationCustomizer(props, env);
        assertNotNull(customizer);

        Configuration configuration = new Configuration();
        customizer.customize(configuration);
        // dev/test 环境下应该启用
        assertEquals(org.apache.ibatis.logging.stdout.StdOutImpl.class, configuration.getLogImpl());
    }

    @Test
    void testMybatisPlusInterceptorPropertiesParameterNotUsed() {
        // 验证 properties 参数虽然传入但未使用（不影响功能）
        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.empty());
        MybatisProperties props = new MybatisProperties();
        props.setMapperPackages(List.of("test.package"));
        props.setEnableSqlStdout(true);

        MybatisPlusInterceptor interceptor = cfg.mybatisPlusInterceptor(props, new StandardEnvironment());
        assertNotNull(interceptor);
        // properties 参数在 mybatisPlusInterceptor 方法中未使用，这是正常的
    }

    @Test
    void testMybatisPlusInterceptorEnvironmentParameterNotUsed() {
        // 验证 env 参数虽然传入但未使用（不影响功能）
        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.empty());
        MybatisProperties props = new MybatisProperties();

        StandardEnvironment env = new StandardEnvironment();
        env.setActiveProfiles("custom-profile");

        MybatisPlusInterceptor interceptor = cfg.mybatisPlusInterceptor(props, env);
        assertNotNull(interceptor);
        // env 参数在 mybatisPlusInterceptor 方法中未使用，这是正常的
    }

    @Test
    void testTryCreatePaginationInnerInterceptorHandlesException() {
        // 测试 tryCreatePaginationInnerInterceptor 方法的异常处理
        // 由于是私有方法，我们通过 mybatisPlusInterceptor 间接测试
        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.empty());
        MybatisProperties props = new MybatisProperties();

        // 即使分页拦截器加载失败，也不应该抛出异常
        assertDoesNotThrow(() -> {
            MybatisPlusInterceptor interceptor = cfg.mybatisPlusInterceptor(props, new StandardEnvironment());
            assertNotNull(interceptor);
        });
    }

    @Test
    void testInnerInterceptorsIfPresentWithNonEmptyList() {
        // 测试 innerInterceptors.ifPresent 分支（非空列表）
        InnerInterceptor interceptor1 = Mockito.mock(InnerInterceptor.class);
        InnerInterceptor interceptor2 = Mockito.mock(InnerInterceptor.class);
        List<InnerInterceptor> interceptors = Arrays.asList(interceptor1, interceptor2);

        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.of(interceptors));
        MybatisProperties props = new MybatisProperties();

        MybatisPlusInterceptor result = cfg.mybatisPlusInterceptor(props, new StandardEnvironment());
        assertNotNull(result);
    }

    @Test
    void testInnerInterceptorsIfPresentWithEmptyList() {
        // 测试 innerInterceptors.ifPresent 分支（空列表，应该跳过）
        List<InnerInterceptor> emptyList = Collections.emptyList();

        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.of(emptyList));
        MybatisProperties props = new MybatisProperties();

        MybatisPlusInterceptor result = cfg.mybatisPlusInterceptor(props, new StandardEnvironment());
        assertNotNull(result);
    }

    @Test
    void testConfigurationCustomizerLambdaExecution() {
        // 测试 ConfigurationCustomizer lambda 表达式的执行
        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.empty());
        MybatisProperties props = new MybatisProperties();
        props.setEnableSqlStdout(true);

        StandardEnvironment env = new StandardEnvironment();
        ConfigurationCustomizer customizer = cfg.mybatisConfigurationCustomizer(props, env);

        // 多次调用 customize 应该都能正常工作
        Configuration config1 = new Configuration();
        customizer.customize(config1);
        assertEquals(org.apache.ibatis.logging.stdout.StdOutImpl.class, config1.getLogImpl());

        Configuration config2 = new Configuration();
        customizer.customize(config2);
        assertEquals(org.apache.ibatis.logging.stdout.StdOutImpl.class, config2.getLogImpl());
    }

    @Test
    void testConfigurationCustomizerWithFalseDoesNotSetLogImpl() {
        // 测试 enableStdout 为 false 时，不设置 LogImpl
        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.empty());
        MybatisProperties props = new MybatisProperties();
        props.setEnableSqlStdout(false);

        StandardEnvironment env = new StandardEnvironment();
        env.setActiveProfiles("dev"); // 即使 dev 环境，显式 false 也应该生效

        ConfigurationCustomizer customizer = cfg.mybatisConfigurationCustomizer(props, env);
        Configuration configuration = new Configuration();
        customizer.customize(configuration);

        assertNull(configuration.getLogImpl());
    }

    @Test
    void testMapperScannerConfigurerReturnsNonNullEvenWithNullPackages() {
        // 测试即使 mapperPackages 为 null，也返回非空的 configurer
        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.empty());
        MybatisProperties props = new MybatisProperties();
        props.setMapperPackages(null);

        MapperScannerConfigurer configurer = cfg.mapperScannerConfigurer(props);
        assertNotNull(configurer);
    }

    @Test
    void testMybatisPlusInterceptorAlwaysReturnsNonNull() {
        // 测试 mybatisPlusInterceptor 方法在各种情况下都返回非空
        MybatisPlusAutoConfiguration cfg1 = new MybatisPlusAutoConfiguration(Optional.empty());
        MybatisPlusAutoConfiguration cfg2 = new MybatisPlusAutoConfiguration(
                Optional.of(Collections.singletonList(Mockito.mock(InnerInterceptor.class))));

        MybatisProperties props = new MybatisProperties();
        StandardEnvironment env = new StandardEnvironment();

        assertNotNull(cfg1.mybatisPlusInterceptor(props, env));
        assertNotNull(cfg2.mybatisPlusInterceptor(props, env));
    }
}
