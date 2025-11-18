package com.yggdrasil.labs.test.annotation;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.*;

/**
 * Mimir Boot 测试注解
 *
 * <p>简化 Spring Boot 测试配置，自动应用常用配置：</p>
 * <ul>
 * <li>使用 test profile</li>
 * <li>配置 Spring Boot 测试环境</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * @MimirBootTest
 * class MyServiceTest {
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
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootTest
@ActiveProfiles("test")
public @interface MimirBootTest {

    /**
     * Spring Boot 应用主类
     *
     * @return 主类
     */
    Class<?>[] classes() default {};

    /**
     * 测试属性配置
     *
     * @return 属性配置
     */
    String[] properties() default {};

    /**
     * 是否使用默认过滤器
     *
     * @return true 使用默认过滤器，false 不使用
     */
    boolean useDefaultFilters() default true;

    /**
     * Web 环境类型
     *
     * @return Web 环境类型
     */
    org.springframework.boot.test.context.SpringBootTest.WebEnvironment webEnvironment()
            default org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;

    /**
     * 排除的自动配置类
     *
     * @return 要排除的自动配置类
     */
    Class<?>[] excludeAutoConfiguration() default {};
}

