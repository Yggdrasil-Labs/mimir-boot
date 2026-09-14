package com.yggdrasil.labs.exception.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * 异常处理配置属性
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "mimir.boot.exception")
public class ExceptionProperties {

    /** 是否启用全局异常处理 */
    private boolean enabled = true;
}
