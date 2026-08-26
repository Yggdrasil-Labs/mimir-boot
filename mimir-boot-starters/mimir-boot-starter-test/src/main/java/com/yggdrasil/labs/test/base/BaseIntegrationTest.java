package com.yggdrasil.labs.test.base;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 集成测试基类
 *
 * <p>提供集成测试的基础功能：</p>
 * <ul>
 * <li>自动配置 Spring Boot 测试环境</li>
 * <li>使用 test profile</li>
 * <li>自动清理测试环境</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * class MyIntegrationTest extends BaseIntegrationTest {
 *     @Autowired
 *     private MyService myService;
 *
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
@SpringBootTest
@ActiveProfiles("test")
public abstract class BaseIntegrationTest extends BaseUnitTest {
}
