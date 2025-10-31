package com.yggdrasil.labs.web.config;

import com.yggdrasil.labs.web.advice.ResponseBodyEnhancer;
import com.yggdrasil.labs.web.interceptor.TraceInterceptor;
import com.yggdrasil.labs.web.interceptor.WebInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Web 层自动配置测试
 *
 * <p>测试 WebAutoConfiguration 的功能：</p>
 * <ul>
 * <li>创建响应体增强器</li>
 * <li>创建 Trace 拦截器</li>
 * <li>创建 Web 拦截器</li>
 * </ul>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class WebAutoConfigurationTest {

    private WebAutoConfiguration configuration;
    private WebProperties webProperties;

    @BeforeEach
    void setUp() {
        webProperties = new WebProperties();
        configuration = new WebAutoConfiguration();
    }

    /**
     * 测试创建响应体增强器
     */
    @Test
    void testResponseBodyEnhancerCreation() {
        ResponseBodyEnhancer enhancer = configuration.responseBodyEnhancer(webProperties);

        assertNotNull(enhancer);
        assertInstanceOf(ResponseBodyEnhancer.class, enhancer);
    }

    /**
     * 测试创建 Trace 拦截器
     */
    @Test
    void testTraceInterceptorCreation() {
        TraceInterceptor interceptor = configuration.traceInterceptor();

        assertNotNull(interceptor);
        assertInstanceOf(TraceInterceptor.class, interceptor);
    }

    /**
     * 测试创建 Web 拦截器
     */
    @Test
    void testWebInterceptorCreation() {
        WebInterceptor interceptor = configuration.webInterceptor();

        assertNotNull(interceptor);
        assertInstanceOf(WebInterceptor.class, interceptor);
    }

    /**
     * 测试多次创建返回不同实例
     */
    @Test
    void testMultipleBeanCreation() {
        ResponseBodyEnhancer enhancer1 = configuration.responseBodyEnhancer(webProperties);
        ResponseBodyEnhancer enhancer2 = configuration.responseBodyEnhancer(webProperties);

        TraceInterceptor traceInterceptor1 = configuration.traceInterceptor();
        TraceInterceptor traceInterceptor2 = configuration.traceInterceptor();

        WebInterceptor webInterceptor1 = configuration.webInterceptor();
        WebInterceptor webInterceptor2 = configuration.webInterceptor();

        assertNotNull(enhancer1);
        assertNotNull(enhancer2);
        // 每次调用都会创建新实例（在 Spring 容器中会通过单例管理）
        assertNotSame(enhancer1, enhancer2);

        assertNotNull(traceInterceptor1);
        assertNotNull(traceInterceptor2);
        assertNotSame(traceInterceptor1, traceInterceptor2);

        assertNotNull(webInterceptor1);
        assertNotNull(webInterceptor2);
        assertNotSame(webInterceptor1, webInterceptor2);
    }
}

