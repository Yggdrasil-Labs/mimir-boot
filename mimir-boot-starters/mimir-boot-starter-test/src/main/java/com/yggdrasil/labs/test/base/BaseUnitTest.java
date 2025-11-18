package com.yggdrasil.labs.test.base;

import com.yggdrasil.labs.test.util.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 单元测试基类
 *
 * <p>提供单元测试的基础功能：</p>
 * <ul>
 * <li>自动初始化 Mockito</li>
 * <li>自动清理测试环境</li>
 * <li>提供测试工具方法</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * class MyServiceTest extends BaseUnitTest {
 *     @Test
 *     void testSomething() {
 *         // 测试代码
 *     }
 * }
 * }</pre>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
public abstract class BaseUnitTest {

    /**
     * 测试前准备
     */
    @BeforeEach
    void setUpBase() {
        // 清理测试环境
        TestUtils.cleanupTestEnvironment();
        // 子类可以重写此方法添加额外的准备逻辑
        setUp();
    }

    /**
     * 测试后清理
     */
    @AfterEach
    void tearDownBase() {
        // 清理测试环境
        TestUtils.cleanupTestEnvironment();
        // 子类可以重写此方法添加额外的清理逻辑
        tearDown();
    }

    /**
     * 子类可以重写此方法添加测试前的准备逻辑
     */
    protected void setUp() {
        // 默认空实现，子类可重写
    }

    /**
     * 子类可以重写此方法添加测试后的清理逻辑
     */
    protected void tearDown() {
        // 默认空实现，子类可重写
    }
}

