package com.yggdrasil.labs.test.config;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * 测试配置类
 *
 * <p>用于测试基类的 Spring Boot 测试配置</p>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(basePackages = "com.yggdrasil.labs.test")
public class TestConfiguration {
    // 空的配置类，用于 Spring Boot 测试
}

