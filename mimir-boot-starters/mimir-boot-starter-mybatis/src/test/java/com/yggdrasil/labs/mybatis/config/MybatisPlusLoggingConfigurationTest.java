package com.yggdrasil.labs.mybatis.config;

import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.yggdrasil.labs.mybatis.log.JsonSqlLogInnerInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MyBatis-Plus 日志配置测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class MybatisPlusLoggingConfigurationTest {

    @Test
    void jsonSqlLogInnerInterceptor_with_null_enableJson_in_dev_environment() {
        MybatisPlusLoggingConfiguration cfg = new MybatisPlusLoggingConfiguration();
        MybatisProperties props = new MybatisProperties();
        props.setEnableJsonSqlLog(null);

        StandardEnvironment env = new StandardEnvironment();
        env.setActiveProfiles("dev");

        InnerInterceptor interceptor = cfg.jsonSqlLogInnerInterceptor(props, env);
        assertNotNull(interceptor);
        assertInstanceOf(JsonSqlLogInnerInterceptor.class, interceptor);
    }

    @Test
    void jsonSqlLogInnerInterceptor_with_null_enableJson_in_test_environment() {
        MybatisPlusLoggingConfiguration cfg = new MybatisPlusLoggingConfiguration();
        MybatisProperties props = new MybatisProperties();
        props.setEnableJsonSqlLog(null);

        StandardEnvironment env = new StandardEnvironment();
        env.setActiveProfiles("test");

        InnerInterceptor interceptor = cfg.jsonSqlLogInnerInterceptor(props, env);
        assertNotNull(interceptor);
        assertInstanceOf(JsonSqlLogInnerInterceptor.class, interceptor);
    }

    @Test
    void jsonSqlLogInnerInterceptor_with_null_enableJson_in_prod_environment() {
        MybatisPlusLoggingConfiguration cfg = new MybatisPlusLoggingConfiguration();
        MybatisProperties props = new MybatisProperties();
        props.setEnableJsonSqlLog(null);

        StandardEnvironment env = new StandardEnvironment();
        env.setActiveProfiles("prod");

        InnerInterceptor interceptor = cfg.jsonSqlLogInnerInterceptor(props, env);
        assertNull(interceptor);
    }

    @Test
    void jsonSqlLogInnerInterceptor_with_null_enableJson_in_default_environment() {
        MybatisPlusLoggingConfiguration cfg = new MybatisPlusLoggingConfiguration();
        MybatisProperties props = new MybatisProperties();
        props.setEnableJsonSqlLog(null);

        StandardEnvironment env = new StandardEnvironment();
        // 不设置任何 profile

        InnerInterceptor interceptor = cfg.jsonSqlLogInnerInterceptor(props, env);
        assertNull(interceptor);
    }

    @Test
    void jsonSqlLogInnerInterceptor_with_explicit_true() {
        MybatisPlusLoggingConfiguration cfg = new MybatisPlusLoggingConfiguration();
        MybatisProperties props = new MybatisProperties();
        props.setEnableJsonSqlLog(true);

        StandardEnvironment env = new StandardEnvironment();
        // 使用默认环境（无 profile），测试显式配置

        InnerInterceptor interceptor = cfg.jsonSqlLogInnerInterceptor(props, env);
        assertNotNull(interceptor);
        assertInstanceOf(JsonSqlLogInnerInterceptor.class, interceptor);
    }

    @Test
    void jsonSqlLogInnerInterceptor_with_explicit_false() {
        MybatisPlusLoggingConfiguration cfg = new MybatisPlusLoggingConfiguration();
        MybatisProperties props = new MybatisProperties();
        props.setEnableJsonSqlLog(false);

        StandardEnvironment env = new StandardEnvironment();
        // 使用默认环境（无 profile），测试显式配置

        InnerInterceptor interceptor = cfg.jsonSqlLogInnerInterceptor(props, env);
        assertNull(interceptor);
    }

    @Test
    void jsonSqlLogInnerInterceptor_explicit_true_overrides_environment() {
        MybatisPlusLoggingConfiguration cfg = new MybatisPlusLoggingConfiguration();
        MybatisProperties props = new MybatisProperties();
        props.setEnableJsonSqlLog(true);

        // 即使在 prod 环境下，显式设置为 true 也应该生效
        StandardEnvironment env = new StandardEnvironment();
        env.setActiveProfiles("prod");

        InnerInterceptor interceptor = cfg.jsonSqlLogInnerInterceptor(props, env);
        assertNotNull(interceptor);
        assertInstanceOf(JsonSqlLogInnerInterceptor.class, interceptor);
    }

    @Test
    void jsonSqlLogInnerInterceptor_explicit_false_overrides_environment() {
        MybatisPlusLoggingConfiguration cfg = new MybatisPlusLoggingConfiguration();
        MybatisProperties props = new MybatisProperties();
        props.setEnableJsonSqlLog(false);

        // 即使在 dev 环境下，显式设置为 false 也应该生效
        StandardEnvironment env = new StandardEnvironment();
        env.setActiveProfiles("dev");

        InnerInterceptor interceptor = cfg.jsonSqlLogInnerInterceptor(props, env);
        assertNull(interceptor);
    }

    @Test
    void jsonSqlLogInnerInterceptor_with_multiple_profiles_including_dev() {
        MybatisPlusLoggingConfiguration cfg = new MybatisPlusLoggingConfiguration();
        MybatisProperties props = new MybatisProperties();
        props.setEnableJsonSqlLog(null);

        StandardEnvironment env = new StandardEnvironment();
        env.setActiveProfiles("dev", "local", "custom");

        InnerInterceptor interceptor = cfg.jsonSqlLogInnerInterceptor(props, env);
        assertNotNull(interceptor);
        assertInstanceOf(JsonSqlLogInnerInterceptor.class, interceptor);
    }

    @Test
    void jsonSqlLogInnerInterceptor_with_multiple_profiles_including_test() {
        MybatisPlusLoggingConfiguration cfg = new MybatisPlusLoggingConfiguration();
        MybatisProperties props = new MybatisProperties();
        props.setEnableJsonSqlLog(null);

        StandardEnvironment env = new StandardEnvironment();
        env.setActiveProfiles("test", "integration", "custom");

        InnerInterceptor interceptor = cfg.jsonSqlLogInnerInterceptor(props, env);
        assertNotNull(interceptor);
        assertInstanceOf(JsonSqlLogInnerInterceptor.class, interceptor);
    }

    @Test
    void jsonSqlLogInnerInterceptor_with_multiple_profiles_excluding_dev_and_test() {
        MybatisPlusLoggingConfiguration cfg = new MybatisPlusLoggingConfiguration();
        MybatisProperties props = new MybatisProperties();
        props.setEnableJsonSqlLog(null);

        StandardEnvironment env = new StandardEnvironment();
        env.setActiveProfiles("prod", "staging", "custom");

        InnerInterceptor interceptor = cfg.jsonSqlLogInnerInterceptor(props, env);
        assertNull(interceptor);
    }

    @Test
    void jsonSqlLogInnerInterceptor_returns_new_instance_each_time() {
        MybatisPlusLoggingConfiguration cfg = new MybatisPlusLoggingConfiguration();
        MybatisProperties props = new MybatisProperties();
        props.setEnableJsonSqlLog(true);

        StandardEnvironment env = new StandardEnvironment();

        InnerInterceptor interceptor1 = cfg.jsonSqlLogInnerInterceptor(props, env);
        InnerInterceptor interceptor2 = cfg.jsonSqlLogInnerInterceptor(props, env);

        assertNotNull(interceptor1);
        assertNotNull(interceptor2);
        // 每次调用都返回新实例
        assertNotSame(interceptor1, interceptor2);
    }

    @Test
    void jsonSqlLogInnerInterceptor_does_not_throw_exception() {
        MybatisPlusLoggingConfiguration cfg = new MybatisPlusLoggingConfiguration();
        MybatisProperties props = new MybatisProperties();
        StandardEnvironment env = new StandardEnvironment();

        assertDoesNotThrow(() -> {
            InnerInterceptor interceptor = cfg.jsonSqlLogInnerInterceptor(props, env);
            // 可能为 null，也可能不为 null，取决于环境
            // 验证方法调用不会抛出异常即可
            assertTrue(interceptor == null || interceptor instanceof JsonSqlLogInnerInterceptor);
        });
    }

    @Test
    void jsonSqlLogInnerInterceptor_with_default_properties() {
        MybatisPlusLoggingConfiguration cfg = new MybatisPlusLoggingConfiguration();
        MybatisProperties props = new MybatisProperties();
        // 不设置 enableJsonSqlLog，使用默认值 null

        StandardEnvironment env = new StandardEnvironment();
        env.setActiveProfiles("dev");

        InnerInterceptor interceptor = cfg.jsonSqlLogInnerInterceptor(props, env);
        assertNotNull(interceptor);
        assertInstanceOf(JsonSqlLogInnerInterceptor.class, interceptor);
    }
}

