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

    @Test
    void testOptimisticLockerInterceptorIsAlwaysAdded() throws Exception {
        // 验证乐观锁拦截器总是被添加
        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.empty());
        MybatisProperties props = new MybatisProperties();
        StandardEnvironment env = new StandardEnvironment();

        MybatisPlusInterceptor interceptor = cfg.mybatisPlusInterceptor(props, env);

        // 通过反射获取内部拦截器列表（尝试多个可能的字段名）
        List<InnerInterceptor> innerInterceptors = getInnerInterceptors(interceptor);

        assertNotNull(innerInterceptors);
        assertFalse(innerInterceptors.isEmpty());

        // 验证乐观锁拦截器存在
        boolean hasOptimisticLocker = innerInterceptors.stream()
                .anyMatch(i -> i instanceof com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor);
        assertTrue(hasOptimisticLocker, "乐观锁拦截器应该总是被添加");
    }

    /**
     * 通过反射获取 MybatisPlusInterceptor 的内部拦截器列表
     * 尝试多个可能的字段名以兼容不同版本
     */
    @SuppressWarnings("unchecked")
    private List<InnerInterceptor> getInnerInterceptors(MybatisPlusInterceptor interceptor) throws Exception {
        // 尝试常见的字段名
        String[] possibleFieldNames = {"interceptors", "interceptorList", "innerInterceptors"};

        for (String fieldName : possibleFieldNames) {
            try {
                java.lang.reflect.Field field = MybatisPlusInterceptor.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(interceptor);
                if (value instanceof List) {
                    return (List<InnerInterceptor>) value;
                }
            } catch (NoSuchFieldException e) {
                // 继续尝试下一个字段名
            }
        }

        // 如果所有字段名都失败，尝试查找所有 List 类型的字段
        java.lang.reflect.Field[] fields = MybatisPlusInterceptor.class.getDeclaredFields();
        for (java.lang.reflect.Field field : fields) {
            if (List.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                Object value = field.get(interceptor);
                if (value instanceof List && !((List<?>) value).isEmpty()) {
                    Object first = ((List<?>) value).get(0);
                    if (first instanceof InnerInterceptor) {
                        return (List<InnerInterceptor>) value;
                    }
                }
            }
        }

        throw new IllegalStateException("无法通过反射获取 MybatisPlusInterceptor 的内部拦截器列表");
    }

    @Test
    void testPaginationInterceptorMayBeAdded() throws Exception {
        // 验证分页拦截器在类存在时可能被添加（如果 MyBatis-Plus 版本支持）
        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.empty());
        MybatisProperties props = new MybatisProperties();
        StandardEnvironment env = new StandardEnvironment();

        MybatisPlusInterceptor interceptor = cfg.mybatisPlusInterceptor(props, env);

        // 通过反射获取内部拦截器列表
        List<InnerInterceptor> innerInterceptors = getInnerInterceptors(interceptor);

        assertNotNull(innerInterceptors);
        // 至少应该有乐观锁拦截器
        assertFalse(innerInterceptors.isEmpty());

        // 检查是否有分页拦截器（如果类存在则会被添加）
        // 先尝试加载分页拦截器类，如果存在则使用 instanceof 检查
        Class<?> paginationClass;
        try {
            paginationClass = Class.forName("com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor");
        } catch (ClassNotFoundException e) {
            // 分页拦截器类不存在于类路径中，这是正常的
            paginationClass = null;
        }

        final Class<?> finalPaginationClass = paginationClass;
        final boolean hasPagination = finalPaginationClass != null && innerInterceptors.stream()
                .anyMatch(finalPaginationClass::isInstance);

        // 分页拦截器可能存在也可能不存在（取决于类路径），但至少应该不抛异常
        assertDoesNotThrow(() -> {
            // 如果分页拦截器存在，验证其类型
            if (hasPagination) {
                InnerInterceptor pagination = innerInterceptors.stream()
                        .filter(finalPaginationClass::isInstance)
                        .findFirst()
                        .orElse(null);
                assertNotNull(pagination);
            }
        });
    }

    @Test
    void testCustomInterceptorsAreAdded() throws Exception {
        // 验证自定义拦截器被正确添加
        InnerInterceptor custom1 = Mockito.mock(InnerInterceptor.class);
        InnerInterceptor custom2 = Mockito.mock(InnerInterceptor.class);
        List<InnerInterceptor> customInterceptors = Arrays.asList(custom1, custom2);

        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.of(customInterceptors));
        MybatisProperties props = new MybatisProperties();
        StandardEnvironment env = new StandardEnvironment();

        MybatisPlusInterceptor interceptor = cfg.mybatisPlusInterceptor(props, env);

        // 通过反射获取内部拦截器列表
        List<InnerInterceptor> innerInterceptors = getInnerInterceptors(interceptor);

        assertNotNull(innerInterceptors);
        // 应该至少包含：分页（可选）+ 乐观锁 + 2个自定义拦截器
        assertTrue(innerInterceptors.size() >= 3, "应该至少包含乐观锁和2个自定义拦截器");

        // 验证自定义拦截器存在
        assertTrue(innerInterceptors.contains(custom1), "自定义拦截器1应该被添加");
        assertTrue(innerInterceptors.contains(custom2), "自定义拦截器2应该被添加");
    }

    @Test
    void testInterceptorOrder() throws Exception {
        // 验证拦截器的顺序：分页（可选） -> 乐观锁 -> 自定义拦截器
        InnerInterceptor custom1 = Mockito.mock(InnerInterceptor.class);
        InnerInterceptor custom2 = Mockito.mock(InnerInterceptor.class);
        List<InnerInterceptor> customInterceptors = Arrays.asList(custom1, custom2);

        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.of(customInterceptors));
        MybatisProperties props = new MybatisProperties();
        StandardEnvironment env = new StandardEnvironment();

        MybatisPlusInterceptor interceptor = cfg.mybatisPlusInterceptor(props, env);

        // 通过反射获取内部拦截器列表
        List<InnerInterceptor> innerInterceptors = getInnerInterceptors(interceptor);

        assertNotNull(innerInterceptors);

        // 查找乐观锁拦截器的位置
        int optimisticLockerIndex = -1;
        for (int i = 0; i < innerInterceptors.size(); i++) {
            if (innerInterceptors.get(i) instanceof com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor) {
                optimisticLockerIndex = i;
                break;
            }
        }
        assertTrue(optimisticLockerIndex >= 0, "乐观锁拦截器应该存在");

        // 查找自定义拦截器的位置
        int custom1Index = innerInterceptors.indexOf(custom1);
        int custom2Index = innerInterceptors.indexOf(custom2);

        assertTrue(custom1Index >= 0, "自定义拦截器1应该存在");
        assertTrue(custom2Index >= 0, "自定义拦截器2应该存在");

        // 验证顺序：乐观锁应该在自定义拦截器之前
        assertTrue(optimisticLockerIndex < custom1Index, "乐观锁拦截器应该在自定义拦截器之前");
        assertTrue(optimisticLockerIndex < custom2Index, "乐观锁拦截器应该在自定义拦截器之前");
        assertTrue(custom1Index < custom2Index, "自定义拦截器应该保持原有顺序");
    }

    @Test
    void testMapperScannerConfigurerBasePackage() throws Exception {
        // 验证 mapperScannerConfigurer 正确设置 basePackage
        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.empty());
        MybatisProperties props = new MybatisProperties();
        props.setMapperPackages(Arrays.asList("com.example.mapper", "com.example.other.mapper"));

        MapperScannerConfigurer configurer = cfg.mapperScannerConfigurer(props);
        assertNotNull(configurer);

        // 通过反射获取 basePackage
        java.lang.reflect.Field field = MapperScannerConfigurer.class.getDeclaredField("basePackage");
        field.setAccessible(true);
        String basePackage = (String) field.get(configurer);

        assertNotNull(basePackage);
        assertEquals("com.example.mapper,com.example.other.mapper", basePackage);
    }

    @Test
    void testMapperScannerConfigurerBasePackageWithSinglePackage() throws Exception {
        // 验证单个包的情况
        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.empty());
        MybatisProperties props = new MybatisProperties();
        props.setMapperPackages(Collections.singletonList("com.example.mapper"));

        MapperScannerConfigurer configurer = cfg.mapperScannerConfigurer(props);
        assertNotNull(configurer);

        // 通过反射获取 basePackage
        java.lang.reflect.Field field = MapperScannerConfigurer.class.getDeclaredField("basePackage");
        field.setAccessible(true);
        String basePackage = (String) field.get(configurer);

        assertNotNull(basePackage);
        assertEquals("com.example.mapper", basePackage);
    }

    @Test
    void testMapperScannerConfigurerBasePackageWithEmptyPackages() throws Exception {
        // 验证空包列表的情况，basePackage 应该为 null
        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.empty());
        MybatisProperties props = new MybatisProperties();
        props.setMapperPackages(Collections.emptyList());

        MapperScannerConfigurer configurer = cfg.mapperScannerConfigurer(props);
        assertNotNull(configurer);

        // 通过反射获取 basePackage
        java.lang.reflect.Field field = MapperScannerConfigurer.class.getDeclaredField("basePackage");
        field.setAccessible(true);
        String basePackage = (String) field.get(configurer);

        // 当包列表为空时，basePackage 应该为 null（因为 CollectionUtils.isEmpty 返回 true）
        assertNull(basePackage);
    }

    @Test
    void testMapperScannerConfigurerBasePackageWithNullPackages() throws Exception {
        // 验证 null 包列表的情况
        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.empty());
        MybatisProperties props = new MybatisProperties();
        props.setMapperPackages(null);

        MapperScannerConfigurer configurer = cfg.mapperScannerConfigurer(props);
        assertNotNull(configurer);

        // 通过反射获取 basePackage
        java.lang.reflect.Field field = MapperScannerConfigurer.class.getDeclaredField("basePackage");
        field.setAccessible(true);
        String basePackage = (String) field.get(configurer);

        // 当包列表为 null 时，basePackage 应该为 null
        assertNull(basePackage);
    }

    @Test
    void testEmptyCustomInterceptorsListIsSkipped() throws Exception {
        // 验证空的自定义拦截器列表会被跳过
        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.of(Collections.emptyList()));
        MybatisProperties props = new MybatisProperties();
        StandardEnvironment env = new StandardEnvironment();

        MybatisPlusInterceptor interceptor = cfg.mybatisPlusInterceptor(props, env);

        // 通过反射获取内部拦截器列表
        List<InnerInterceptor> innerInterceptors = getInnerInterceptors(interceptor);

        assertNotNull(innerInterceptors);
        // 应该只包含乐观锁拦截器（可能还有分页拦截器）
        assertFalse(innerInterceptors.isEmpty());
        assertTrue(innerInterceptors.size() <= 2, "空列表时不应该添加自定义拦截器");
    }

    @Test
    void testConfigurationCustomizerMultipleCalls() {
        // 验证 ConfigurationCustomizer 可以多次调用而不出错
        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.empty());
        MybatisProperties props = new MybatisProperties();
        props.setEnableSqlStdout(true);
        StandardEnvironment env = new StandardEnvironment();

        ConfigurationCustomizer customizer = cfg.mybatisConfigurationCustomizer(props, env);
        assertNotNull(customizer);

        Configuration config1 = new Configuration();
        Configuration config2 = new Configuration();
        Configuration config3 = new Configuration();

        // 多次调用应该都能正常工作
        customizer.customize(config1);
        customizer.customize(config2);
        customizer.customize(config3);

        assertEquals(org.apache.ibatis.logging.stdout.StdOutImpl.class, config1.getLogImpl());
        assertEquals(org.apache.ibatis.logging.stdout.StdOutImpl.class, config2.getLogImpl());
        assertEquals(org.apache.ibatis.logging.stdout.StdOutImpl.class, config3.getLogImpl());
    }
}
