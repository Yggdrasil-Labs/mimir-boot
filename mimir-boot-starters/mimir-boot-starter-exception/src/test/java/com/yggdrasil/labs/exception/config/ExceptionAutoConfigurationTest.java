package com.yggdrasil.labs.exception.config;

import com.yggdrasil.labs.exception.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 异常处理自动配置测试
 *
 * <p>测试自动配置的基本功能</p>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class ExceptionAutoConfigurationTest {

    private ExceptionAutoConfiguration configuration;

    @BeforeEach
    void setUp() {
        configuration = new ExceptionAutoConfiguration();
    }

    /**
     * 测试配置类的默认值
     */
    @Test
    void testConfigurationWithDefaultSettings() {
        assertNotNull(configuration);
    }

    /**
     * 测试创建 GlobalExceptionHandler Bean
     */
    @Test
    void testGlobalExceptionHandlerCreation() {
        GlobalExceptionHandler handler = configuration.globalExceptionHandler();

        assertNotNull(handler);
        assertInstanceOf(GlobalExceptionHandler.class, handler);
    }

    /**
     * 测试多次调用返回不同的实例
     */
    @Test
    void testMultipleHandlerCreation() {
        GlobalExceptionHandler handler1 = configuration.globalExceptionHandler();
        GlobalExceptionHandler handler2 = configuration.globalExceptionHandler();

        assertNotNull(handler1);
        assertNotNull(handler2);
        // 每次调用都会创建新实例（在 Spring 容器中会通过单例管理）
        assertNotSame(handler1, handler2);
    }
}

