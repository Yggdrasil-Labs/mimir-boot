package com.yggdrasil.labs.test.base;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

import com.yggdrasil.labs.test.config.TestConfiguration;
import com.yggdrasil.labs.test.util.TestUtils;

/**
 * BaseWebTest 测试基类测试
 *
 * <p>注意：这是一个集成测试，需要 Spring 上下文和 MockMvc
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@SpringBootTest(classes = TestConfiguration.class)
class BaseWebTestTest extends BaseWebTest {

    @Autowired private ApplicationContext applicationContext;

    @Autowired private MockMvc mockMvc;

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
        assertNotNull(mockMvc, "BaseWebTest 应自动配置 MockMvc");
    }

    @Test
    void testBaseWebTest_SpringContext() {
        assertNotNull(applicationContext, "Spring ApplicationContext 应已注入");
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
        assertTrue(
                Arrays.asList(applicationContext.getEnvironment().getActiveProfiles())
                        .contains("test"),
                "BaseIntegrationTest 应通过继承激活 test profile");
    }

    @Test
    void testBaseWebTest_ReusesIntegrationLifecycle() {
        assertEquals(
                BaseIntegrationTest.class,
                BaseWebTest.class.getSuperclass(),
                "Web 测试基类应复用统一的 setup/teardown 生命周期");
    }
}
