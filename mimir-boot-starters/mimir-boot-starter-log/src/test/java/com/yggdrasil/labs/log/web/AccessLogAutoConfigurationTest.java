package com.yggdrasil.labs.log.web;

import com.yggdrasil.labs.test.base.BaseUnitTest;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 访问日志自动配置测试
 *
 * <p>测试配置属性的基本功能</p>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class AccessLogAutoConfigurationTest extends BaseUnitTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AccessLogAutoConfiguration.class));

    private AccessLogAutoConfiguration configuration;

    @Override
    @BeforeEach
    public void setUp() {
        super.setUp();
        AccessLogProperties properties = new AccessLogProperties();
        configuration = new AccessLogAutoConfiguration(properties);
    }

    /**
     * 测试配置类的默认值
     */
    @Test
    void testConfigurationWithDefaultSettings() {
        assertNotNull(configuration);
    }

    /**
     * 测试配置类的自定义值
     */
    @Test
    void testConfigurationWithCustomSettings() {
        AccessLogProperties properties = new AccessLogProperties();
        properties.setEnabled(true);
        properties.setSlowThresholdMs(2000);

        AccessLogAutoConfiguration config = new AccessLogAutoConfiguration(properties);

        assertNotNull(config);
        assertTrue(properties.isEnabled());
        assertEquals(2000, properties.getSlowThresholdMs());
    }

    /**
     * 测试配置禁用
     */
    @Test
    void testConfigurationDisabled() {
        AccessLogProperties properties = new AccessLogProperties();
        properties.setEnabled(false);

        AccessLogAutoConfiguration config = new AccessLogAutoConfiguration(properties);

        assertNotNull(config);
        assertFalse(properties.isEnabled());
    }

    /**
     * 测试创建 Filter Bean
     */
    @Test
    void testAccessLogFilterCreation() {
        FilterRegistrationBean<?> filter = configuration.accessLogFilter();

        assertNotNull(filter);
        assertEquals("accessLogFilter", filter.getFilterName());
        assertTrue(filter.getUrlPatterns().contains("/*"));
    }

    @Test
    void enablesAsyncSupport() {
        FilterRegistrationBean<?> filter = configuration.accessLogFilter();

        assertTrue(filter.isAsyncSupported());
    }

    @Test
    void customAccessLogFilterByReservedNameOverridesDefault() {
        runner.withBean("accessLogFilter", FilterRegistrationBean.class,
                        () -> new FilterRegistrationBean<>(new AccessLogFilter(500, java.util.List.of())))
                .run(ctx -> assertEquals(1, ctx.getBeansOfType(FilterRegistrationBean.class).size()));
    }

    /**
     * 测试配置不同的慢接口阈值
     */
    @Test
    void testDifferentSlowThresholds() {
        int[] thresholds = {500, 1000, 2000, 5000, 10000};

        for (int threshold : thresholds) {
            AccessLogProperties properties = new AccessLogProperties();
            properties.setSlowThresholdMs(threshold);

            AccessLogAutoConfiguration config = new AccessLogAutoConfiguration(properties);
            FilterRegistrationBean<Filter> filter = config.accessLogFilter();

            assertNotNull(filter);
        }
    }
}
