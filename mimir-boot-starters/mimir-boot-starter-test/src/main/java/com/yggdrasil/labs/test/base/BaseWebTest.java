package com.yggdrasil.labs.test.base;

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
public abstract class BaseWebTest extends BaseIntegrationTest {
}
