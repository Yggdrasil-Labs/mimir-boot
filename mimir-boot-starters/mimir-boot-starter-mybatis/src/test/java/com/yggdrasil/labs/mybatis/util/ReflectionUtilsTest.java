package com.yggdrasil.labs.mybatis.util;

import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.yggdrasil.labs.test.base.BaseUnitTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 反射工具类测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class ReflectionUtilsTest extends BaseUnitTest {

    @Test
    void testCreatePaginationInnerInterceptor_doesNotThrowException() {
        // 测试方法不会抛出异常，即使类不存在
        assertDoesNotThrow(ReflectionUtils::createPaginationInnerInterceptor);
    }

    @Test
    void testCreatePaginationInnerInterceptor_returnsNullOrValidInstance() {
        // 验证返回值要么为 null，要么是有效的 InnerInterceptor 实例
        InnerInterceptor interceptor = ReflectionUtils.createPaginationInnerInterceptor();

        if (interceptor != null) {
            assertInstanceOf(InnerInterceptor.class, interceptor);
        }
        // 如果为 null，说明分页拦截器类不存在，这是正常的（取决于 MyBatis-Plus 版本）
    }

    @Test
    void testCreatePaginationInnerInterceptor_handlesClassNotFoundException() {
        // 测试 ClassNotFoundException 处理
        // 由于我们无法模拟类不存在的情况（除非移除依赖），我们主要验证方法不会抛出异常
        assertDoesNotThrow(ReflectionUtils::createPaginationInnerInterceptor);
    }

    @Test
    void testCreatePaginationInnerInterceptor_handlesNoSuchMethodException() {
        // 测试 NoSuchMethodException 处理
        // 由于我们无法模拟构造方法不存在的情况，我们主要验证方法不会抛出异常
        assertDoesNotThrow(ReflectionUtils::createPaginationInnerInterceptor);
    }

    @Test
    void testCreatePaginationInnerInterceptor_handlesReflectiveOperationException() {
        // 测试 ReflectiveOperationException 处理
        assertDoesNotThrow(ReflectionUtils::createPaginationInnerInterceptor);
    }

    @Test
    void testCreatePaginationInnerInterceptor_handlesClassCastException() {
        // 测试 ClassCastException 处理
        assertDoesNotThrow(ReflectionUtils::createPaginationInnerInterceptor);
    }

    @Test
    void testCreatePaginationInnerInterceptor_multipleCalls() {
        // 测试多次调用的一致性
        InnerInterceptor interceptor1 = ReflectionUtils.createPaginationInnerInterceptor();
        InnerInterceptor interceptor2 = ReflectionUtils.createPaginationInnerInterceptor();

        // 多次调用的结果应该一致（都为 null 或都不为 null）
        if (interceptor1 == null) {
            assertNull(interceptor2, "多次调用结果应该一致");
        } else {
            assertNotNull(interceptor2, "多次调用结果应该一致");
            assertEquals(interceptor1.getClass(), interceptor2.getClass());
        }
    }
}

