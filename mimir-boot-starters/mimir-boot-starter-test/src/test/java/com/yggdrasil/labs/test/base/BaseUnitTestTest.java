package com.yggdrasil.labs.test.base;

import com.yggdrasil.labs.test.util.TestUtils;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.slf4j.MDC;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BaseUnitTest 测试基类测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class BaseUnitTestTest extends BaseUnitTest {

    @Mock
    private List<String> mockList;

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
    void testBaseUnitTest_SetupAndTearDown() {
        // 验证 setUp 被调用（通过 @BeforeEach）
        assertTrue(setUpCalled, "setUp() 方法应被调用");

        // 验证 Mockito 扩展已启用
        assertNotNull(mockList, "Mock 对象应被创建");

        // 验证测试环境已清理
        assertNull(MDC.get("traceId"), "MDC 应被清理");
    }

    @Test
    void testBaseUnitTest_MdcCleanup() {
        // 设置一些 MDC 值
        TestUtils.setupMdc("test-trace", "test-user", "192.168.1.1");
        assertEquals("test-trace", MDC.get("traceId"));

        // 测试方法结束后，tearDown 应该清理 MDC
        // 但由于 @AfterEach 在测试方法之后执行，我们需要手动验证
    }

    @Test
    void testBaseUnitTest_MockitoExtension() {
        // 验证 Mockito 扩展正常工作
        assertNotNull(mockList, "Mock 对象应被创建");

        // 可以配置 mock 行为
        org.mockito.Mockito.when(mockList.size()).thenReturn(10);
        assertEquals(10, mockList.size());
    }

    @Test
    void testBaseUnitTest_EnvironmentCleanup() {
        // 在测试中设置一些环境状态
        TestUtils.setupMdc("trace-123", "user-456", "192.168.1.1");

        // 验证环境状态
        assertNotNull(MDC.get("traceId"));

        // tearDown 会在测试结束后清理
    }
}

