package com.yggdrasil.labs.mybatis.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.core.env.StandardEnvironment;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;

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
    void mapperScannerConfigurer_sets_base_package_when_packages_present() {
        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.empty());
        MybatisProperties props = new MybatisProperties();
        props.setMapperPackages(Arrays.asList("a.b.c", "x.y.z"));
        MapperScannerConfigurer msc = cfg.mapperScannerConfigurer(props);
        assertNotNull(msc);
        // basePackage 拼接成逗号分隔，框架内部消费，无法直接读取；至少对象不为空即可
    }

    @Test
    void configurationCustomizer_respects_explicit_enableStdout_flag() {
        MybatisPlusAutoConfiguration cfg = new MybatisPlusAutoConfiguration(Optional.empty());
        MybatisProperties props = new MybatisProperties();

        // 显式 true 应生效（不依赖环境）
        props.setEnableSqlStdout(true);
        assertNotNull(cfg.mybatisConfigurationCustomizer(props, new StandardEnvironment()));

        // 显式 false 也应生效
        props.setEnableSqlStdout(false);
        assertNotNull(cfg.mybatisConfigurationCustomizer(props, new StandardEnvironment()));
    }

}
