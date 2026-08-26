package com.yggdrasil.labs.test.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Profile;

/**
 * 测试自动配置类
 *
 * <p>提供测试环境的自动配置：</p>
 * <ul>
 * <li>测试环境的特殊处理</li>
 * <li>测试专用的配置</li>
 * </ul>
 *
 * <p>此配置类仅在 test profile 激活时生效</p>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@AutoConfiguration
@Profile("test")
@Deprecated(since = "2.2.1", forRemoval = false)
public class TestAutoConfiguration {
    // 测试环境的自动配置
    // 可以根据需要添加测试专用的 Bean 配置
}
