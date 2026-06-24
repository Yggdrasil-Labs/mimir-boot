package com.yggdrasil.labs.exception.config;

import com.yggdrasil.labs.exception.handler.DefaultExceptionResponseFactory;
import com.yggdrasil.labs.exception.handler.ExceptionResponseFactory;
import com.yggdrasil.labs.exception.handler.MimirExceptionHandler;
import com.yggdrasil.labs.test.base.BaseUnitTest;

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
class ExceptionAutoConfigurationTest extends BaseUnitTest {

    private ExceptionAutoConfiguration configuration;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();
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
     * 测试创建 ExceptionResponseFactory Bean
     */
    @Test
    void testExceptionResponseFactoryCreation() {
        ExceptionResponseFactory factory = configuration.exceptionResponseFactory();

        assertNotNull(factory);
        assertInstanceOf(DefaultExceptionResponseFactory.class, factory);
    }

    /**
     * 测试创建 MimirExceptionHandler Bean
     */
    @Test
    void testMimirExceptionHandlerCreation() {
        ExceptionResponseFactory factory = configuration.exceptionResponseFactory();
        MimirExceptionHandler handler = configuration.mimirExceptionHandler(factory);

        assertNotNull(handler);
        assertInstanceOf(MimirExceptionHandler.class, handler);
    }

    /**
     * 测试多次调用返回不同的实例
     */
    @Test
    void testMultipleHandlerCreation() {
        ExceptionResponseFactory factory = configuration.exceptionResponseFactory();
        MimirExceptionHandler handler1 = configuration.mimirExceptionHandler(factory);
        MimirExceptionHandler handler2 = configuration.mimirExceptionHandler(factory);

        assertNotNull(handler1);
        assertNotNull(handler2);
        assertNotSame(handler1, handler2);
    }
}

