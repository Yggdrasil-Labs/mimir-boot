package com.yggdrasil.labs.mybatis.config;

import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.yggdrasil.labs.mybatis.log.JsonSqlLogInnerInterceptor;
import com.yggdrasil.labs.test.base.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MyBatis-Plus 日志配置测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class MybatisPlusLoggingConfigurationTest extends BaseUnitTest {

    @Test
    void jsonSqlLogInnerInterceptor_with_null_enableJson_in_dev_environment() {
        MybatisProperties props = new MybatisProperties();
        props.setEnableJsonSqlLog(null);

        StandardEnvironment env = new StandardEnvironment();
        env.setActiveProfiles("dev");

        MybatisPlusLoggingConfiguration cfg = new MybatisPlusLoggingConfiguration(props, env);
        InnerInterceptor interceptor = cfg.jsonSqlLogInnerInterceptor();
        assertNotNull(interceptor);
        assertInstanceOf(JsonSqlLogInnerInterceptor.class, interceptor);
    }

    @Test
    void jsonSqlLogInnerInterceptor_with_null_enableJson_in_test_environment() {
        MybatisProperties props = new MybatisProperties();
        props.setEnableJsonSqlLog(null);

        StandardEnvironment env = new StandardEnvironment();
        env.setActiveProfiles("test");

        MybatisPlusLoggingConfiguration cfg = new MybatisPlusLoggingConfiguration(props, env);
        InnerInterceptor interceptor = cfg.jsonSqlLogInnerInterceptor();
        assertNotNull(interceptor);
        assertInstanceOf(JsonSqlLogInnerInterceptor.class, interceptor);
    }

    @Test
    void jsonSqlLogInnerInterceptor_with_null_enableJson_in_prod_environment() {
        MybatisProperties props = new MybatisProperties();
        props.setEnableJsonSqlLog(null);

        StandardEnvironment env = new StandardEnvironment();
        env.setActiveProfiles("prod");

        MybatisPlusLoggingConfiguration cfg = new MybatisPlusLoggingConfiguration(props, env);
        InnerInterceptor interceptor = cfg.jsonSqlLogInnerInterceptor();
        // 生产环境未显式启用时，返回空操作拦截器
        assertNotNull(interceptor);
        assertFalse(interceptor instanceof JsonSqlLogInnerInterceptor);
    }

    @Test
    void jsonSqlLogInnerInterceptor_with_null_enableJson_in_default_environment() {
        MybatisProperties props = new MybatisProperties();
        props.setEnableJsonSqlLog(null);

        StandardEnvironment env = new StandardEnvironment();
        // 不设置任何 profile

        MybatisPlusLoggingConfiguration cfg = new MybatisPlusLoggingConfiguration(props, env);
        InnerInterceptor interceptor = cfg.jsonSqlLogInnerInterceptor();
        // 默认环境未显式启用时，返回空操作拦截器
        assertNotNull(interceptor);
        assertFalse(interceptor instanceof JsonSqlLogInnerInterceptor);
    }

    @Test
    void jsonSqlLogInnerInterceptor_with_explicit_true() {
        MybatisProperties props = new MybatisProperties();
        props.setEnableJsonSqlLog(true);

        StandardEnvironment env = new StandardEnvironment();
        // 使用默认环境（无 profile），测试显式配置

        MybatisPlusLoggingConfiguration cfg = new MybatisPlusLoggingConfiguration(props, env);
        InnerInterceptor interceptor = cfg.jsonSqlLogInnerInterceptor();
        assertNotNull(interceptor);
        assertInstanceOf(JsonSqlLogInnerInterceptor.class, interceptor);
    }

    @Test
    void jsonSqlLogInnerInterceptor_with_explicit_false() {
        MybatisProperties props = new MybatisProperties();
        props.setEnableJsonSqlLog(false);

        StandardEnvironment env = new StandardEnvironment();
        // 使用默认环境（无 profile），测试显式配置

        MybatisPlusLoggingConfiguration cfg = new MybatisPlusLoggingConfiguration(props, env);
        InnerInterceptor interceptor = cfg.jsonSqlLogInnerInterceptor();
        // 显式禁用时，返回空操作拦截器
        assertNotNull(interceptor);
        assertFalse(interceptor instanceof JsonSqlLogInnerInterceptor);
    }

    @Test
    void jsonSqlLogInnerInterceptor_explicit_true_overrides_environment() {
        MybatisProperties props = new MybatisProperties();
        props.setEnableJsonSqlLog(true);

        // 即使在 prod 环境下，显式设置为 true 也应该生效
        StandardEnvironment env = new StandardEnvironment();
        env.setActiveProfiles("prod");

        MybatisPlusLoggingConfiguration cfg = new MybatisPlusLoggingConfiguration(props, env);
        InnerInterceptor interceptor = cfg.jsonSqlLogInnerInterceptor();
        assertNotNull(interceptor);
        assertInstanceOf(JsonSqlLogInnerInterceptor.class, interceptor);
    }

    @Test
    void jsonSqlLogInnerInterceptor_explicit_false_overrides_environment() {
        MybatisProperties props = new MybatisProperties();
        props.setEnableJsonSqlLog(false);

        // 即使在 dev 环境下，显式设置为 false 也应该生效
        StandardEnvironment env = new StandardEnvironment();
        env.setActiveProfiles("dev");

        MybatisPlusLoggingConfiguration cfg = new MybatisPlusLoggingConfiguration(props, env);
        InnerInterceptor interceptor = cfg.jsonSqlLogInnerInterceptor();
        // 显式禁用时，返回空操作拦截器
        assertNotNull(interceptor);
        assertFalse(interceptor instanceof JsonSqlLogInnerInterceptor);
    }

    @Test
    void jsonSqlLogInnerInterceptor_with_multiple_profiles_including_dev() {
        MybatisProperties props = new MybatisProperties();
        props.setEnableJsonSqlLog(null);

        StandardEnvironment env = new StandardEnvironment();
        env.setActiveProfiles("dev", "local", "custom");

        MybatisPlusLoggingConfiguration cfg = new MybatisPlusLoggingConfiguration(props, env);
        InnerInterceptor interceptor = cfg.jsonSqlLogInnerInterceptor();
        assertNotNull(interceptor);
        assertInstanceOf(JsonSqlLogInnerInterceptor.class, interceptor);
    }

    @Test
    void jsonSqlLogInnerInterceptor_with_multiple_profiles_including_test() {
        MybatisProperties props = new MybatisProperties();
        props.setEnableJsonSqlLog(null);

        StandardEnvironment env = new StandardEnvironment();
        env.setActiveProfiles("test", "integration", "custom");

        MybatisPlusLoggingConfiguration cfg = new MybatisPlusLoggingConfiguration(props, env);
        InnerInterceptor interceptor = cfg.jsonSqlLogInnerInterceptor();
        assertNotNull(interceptor);
        assertInstanceOf(JsonSqlLogInnerInterceptor.class, interceptor);
    }

    @Test
    void jsonSqlLogInnerInterceptor_with_multiple_profiles_excluding_dev_and_test() {
        MybatisProperties props = new MybatisProperties();
        props.setEnableJsonSqlLog(null);

        StandardEnvironment env = new StandardEnvironment();
        env.setActiveProfiles("prod", "staging", "custom");

        MybatisPlusLoggingConfiguration cfg = new MybatisPlusLoggingConfiguration(props, env);
        InnerInterceptor interceptor = cfg.jsonSqlLogInnerInterceptor();
        // 生产环境未显式启用时，返回空操作拦截器
        assertNotNull(interceptor);
        assertFalse(interceptor instanceof JsonSqlLogInnerInterceptor);
    }

    @Test
    void jsonSqlLogInnerInterceptor_returns_new_instance_each_time() {
        MybatisProperties props = new MybatisProperties();
        props.setEnableJsonSqlLog(true);

        StandardEnvironment env = new StandardEnvironment();

        MybatisPlusLoggingConfiguration cfg = new MybatisPlusLoggingConfiguration(props, env);
        InnerInterceptor interceptor1 = cfg.jsonSqlLogInnerInterceptor();
        InnerInterceptor interceptor2 = cfg.jsonSqlLogInnerInterceptor();

        assertNotNull(interceptor1);
        assertNotNull(interceptor2);
        // 每次调用都返回新实例
        assertNotSame(interceptor1, interceptor2);
    }

    @Test
    void jsonSqlLogInnerInterceptor_does_not_throw_exception() {
        MybatisProperties props = new MybatisProperties();
        StandardEnvironment env = new StandardEnvironment();

        MybatisPlusLoggingConfiguration cfg = new MybatisPlusLoggingConfiguration(props, env);
        assertDoesNotThrow(() -> {
            InnerInterceptor interceptor = cfg.jsonSqlLogInnerInterceptor();
            // 验证方法调用不会抛出异常即可
            assertNotNull(interceptor);
        });
    }

    @Test
    void jsonSqlLogInnerInterceptor_with_default_properties() {
        MybatisProperties props = new MybatisProperties();
        // 不设置 enableJsonSqlLog，使用默认值 null

        StandardEnvironment env = new StandardEnvironment();
        env.setActiveProfiles("dev");

        MybatisPlusLoggingConfiguration cfg = new MybatisPlusLoggingConfiguration(props, env);
        InnerInterceptor interceptor = cfg.jsonSqlLogInnerInterceptor();
        assertNotNull(interceptor);
        assertInstanceOf(JsonSqlLogInnerInterceptor.class, interceptor);
    }
}

