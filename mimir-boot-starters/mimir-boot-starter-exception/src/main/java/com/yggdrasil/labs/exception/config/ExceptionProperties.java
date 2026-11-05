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

    /** 校验错误输出配置。 */
    private Validation validation = new Validation();

    /** 校验错误的可选输出格式。 */
    @Data
    public static class Validation {
        /** 是否输出结构化且不包含请求值的校验明细。 */
        private boolean structuredDetailsEnabled = false;
    }
}
