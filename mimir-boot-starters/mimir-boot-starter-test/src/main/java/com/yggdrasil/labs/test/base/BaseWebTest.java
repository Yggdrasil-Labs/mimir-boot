package com.yggdrasil.labs.test.base;

import com.yggdrasil.labs.test.util.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Web 集成测试基类
 *
 * <p>提供 Web 层集成测试的基础功能：</p>
 * <ul>
 * <li>自动配置 Spring Boot 测试环境</li>
 * <li>自动配置 MockMvc</li>
 * <li>使用 test profile</li>
 * <li>自动清理测试环境</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * class MyControllerTest extends BaseWebTest {
 *     @Autowired
 *     private MockMvc mockMvc;
 *
 *     @Test
 *     void testEndpoint() throws Exception {
 *         mockMvc.perform(get("/api/test"))
 *             .andExpect(status().isOk());
 *     }
 * }
 * }</pre>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseWebTest {

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

