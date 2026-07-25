package com.yggdrasil.labs.test.annotation;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AliasFor;
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
    @AliasFor(annotation = SpringBootTest.class, attribute = "classes")
    Class<?>[] classes() default {};

    /**
     * 测试属性配置
     *
     * @return 属性配置
     */
    @AliasFor(annotation = SpringBootTest.class, attribute = "properties")
    String[] properties() default {};

    /**
     * 已弃用的兼容属性，此属性从未影响 Spring Boot 测试过滤行为。
     *
     * @return 始终仅作为注解元数据保留
     * @deprecated 无有效语义，仅为保持 2.x 源兼容而保留
     */
    @Deprecated(since = "2.1.1", forRemoval = false)
    boolean useDefaultFilters() default true;

    /**
     * Web 环境类型
     *
     * @return Web 环境类型
     */
    @AliasFor(annotation = SpringBootTest.class, attribute = "webEnvironment")
    SpringBootTest.WebEnvironment webEnvironment() default SpringBootTest.WebEnvironment.MOCK;

    /**
     * 已弃用的兼容属性，此属性从未排除 Spring Boot 自动配置。
     *
     * @return 始终仅作为注解元数据保留
     * @deprecated 无有效语义，仅为保持 2.x 源兼容而保留
     */
    @Deprecated(since = "2.1.1", forRemoval = false)
    Class<?>[] excludeAutoConfiguration() default {};
}
