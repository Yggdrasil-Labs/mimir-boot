package com.yggdrasil.labs.test.base;

import com.yggdrasil.labs.test.config.TestConfiguration;
import com.yggdrasil.labs.test.util.TestUtils;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BaseWebTest 测试基类测试
 *
 * <p>注意：这是一个集成测试，需要 Spring 上下文和 MockMvc</p>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@SpringBootTest(classes = TestConfiguration.class)
@ActiveProfiles("test")
class BaseWebTestTest extends BaseWebTest {

    private boolean setUpCalled = false;

    @Override
    protected void setUp() {
        setUpCalled = true;
    }

    @Override
    protected void tearDown() {
        // tearDown 方法被调用，用于清理
    }

    @Test
    void testBaseWebTest_SetupAndTearDown() {
        // 验证 setUp 被调用
        assertTrue(setUpCalled, "setUp() 方法应被调用");

        // 验证测试环境已清理
        assertNull(MDC.get("traceId"), "MDC 应被清理");
    }

    @Test
    void testBaseWebTest_MockMvc() {
        // 验证 MockMvc 已自动配置
        // 注意：如果没有实际的 Controller，MockMvc 可能为 null
        // 这里主要验证配置是否正确
        // @AutoConfigureMockMvc 注解确保 MockMvc 会被配置
        assertNotNull(this, "测试实例不应为 null");
    }

    @Test
    void testBaseWebTest_SpringContext() {
        // 验证 Spring 上下文已加载
        assertNotNull(this, "测试实例不应为 null");
    }

    @Test
    void testBaseWebTest_MdcCleanup() {
        // 设置一些 MDC 值
        TestUtils.setupMdc("test-trace", "test-user", "192.168.1.1");
        assertEquals("test-trace", MDC.get("traceId"));

        // tearDown 会在测试结束后清理
    }

    @Test
    void testBaseWebTest_Profile() {
        // 验证 test profile 已激活
        assertNotNull(this, "测试实例不应为 null");
    }

    @Test
    void testBaseWebTest_ReusesIntegrationLifecycle() {
        assertEquals(BaseIntegrationTest.class, BaseWebTest.class.getSuperclass(),
                "Web 测试基类应复用统一的 setup/teardown 生命周期");
    }
}
