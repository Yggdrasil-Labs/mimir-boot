package com.yggdrasil.labs.log.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** JSON 日志启动期配置，由早期处理器逐项绑定，不注册为 Bean。 */
@ConfigurationProperties(prefix = "mimir.boot.log")
public class LogJsonProperties {

    /** 日志输出格式，缺省沿用文本。 */
    private LogFormat format = LogFormat.TEXT;

    /** 显式环境名称；空白按未设置处理。 */
    private String environment;

    /** 实例名称；空白按未设置处理。 */
    private String instance;

    private final Json json = new Json();

    public LogFormat getFormat() {
        return format;
    }

    public void setFormat(LogFormat format) {
        this.format = format;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getInstance() {
        return instance;
    }

    public void setInstance(String instance) {
        this.instance = instance;
    }

    public Json getJson() {
        return json;
    }

    /** JSON 字段加工设置。 */
    public static class Json {

        /** 不写入 context 的 MDC 键。 */
        private List<String> excludeMdcKeys = new ArrayList<>();

        /** JSON Pointer 对应的最大 Unicode 码点数；键须使用方括号配置。 */
        private Map<String, Integer> fieldLengthLimits = new LinkedHashMap<>();

        public List<String> getExcludeMdcKeys() {
            return excludeMdcKeys;
        }

        public void setExcludeMdcKeys(List<String> excludeMdcKeys) {
            this.excludeMdcKeys = excludeMdcKeys;
        }

        public Map<String, Integer> getFieldLengthLimits() {
            return fieldLengthLimits;
        }

        public void setFieldLengthLimits(Map<String, Integer> fieldLengthLimits) {
            this.fieldLengthLimits = fieldLengthLimits;
        }
    }
}
