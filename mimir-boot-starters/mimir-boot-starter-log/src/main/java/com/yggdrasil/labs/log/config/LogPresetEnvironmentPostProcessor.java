package com.yggdrasil.labs.log.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.springframework.boot.DefaultPropertiesPropertySource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.ClassUtils;

import com.yggdrasil.labs.log.config.LogPresetSupport.Preset;
import com.yggdrasil.labs.log.converter.MaskListCodec;
import com.yggdrasil.labs.log.json.JsonPointer;
import com.yggdrasil.labs.log.json.LogJsonKeys;
import com.yggdrasil.labs.log.json.LogJsonSettingsCodec;

/**
 * 日志预设早期处理器
 *
 * <p>在全部 EnvironmentPostProcessor 之后、Boot 正式初始化日志之前，识别 {@code logging.config} 选择的 Mimir
 * 预设，严格校验并生成内部属性源：服务器预设的规范化数值与已配置的脱敏规则，由预设 XML 的 springProperty 写入 LoggerContext（D-101）。
 * 不触碰转换器静态状态。JSON 配置始终严格校验，JSON 格式还要求预设、Logback 后端与编码器依赖。Spring Cloud legacy bootstrap
 * 应用只移除两个残留内部源（D-100）。
 *
 * @author Yggdrasil Labs
 * @since 2.4.0
 */
public class LogPresetEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /**
     * 以最低优先级排在 EnvironmentPostProcessor 末尾，以读到配置中心等处理器加入的源；与同为最低优先级的处理器之间先后不作保证。仍早于 Boot 日志正式初始化
     */
    public static final int ORDER = Ordered.LOWEST_PRECEDENCE;

    private static final String LOGBACK_CONTEXT_CLASS = "ch.qos.logback.classic.LoggerContext";
    private static final String LOGBACK_CONVERTER_CLASS =
            "ch.qos.logback.classic.pattern.ClassicConverter";
    private static final String LOGBACK_LOGGING_SYSTEM =
            "org.springframework.boot.logging.logback.LogbackLoggingSystem";
    private static final String JSON_ENCODER_CLASS =
            "net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder";
    private static final String JACKSON_MAPPER_CLASS =
            "com.fasterxml.jackson.databind.ObjectMapper";
    private static final String FORMAT_PROPERTY = "mimir.boot.log.format";
    private static final String ENVIRONMENT_PROPERTY = "mimir.boot.log.environment";
    private static final String INSTANCE_PROPERTY = "mimir.boot.log.instance";
    private static final String EXCLUDE_MDC_KEYS_PROPERTY = "mimir.boot.log.json.exclude-mdc-keys";
    private static final String FIELD_LENGTH_LIMITS_PROPERTY =
            "mimir.boot.log.json.field-length-limits";

    private final Log log;

    public LogPresetEnvironmentPostProcessor(DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(LogPresetEnvironmentPostProcessor.class);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment environment, SpringApplication application) {
        // 每次调用都移除上一轮内部源：复用 Environment 的顺序启动、legacy bootstrap 复制主环境源时均不残留
        environment.getPropertySources().remove(LogPresetSupport.RESOLVED_PROPERTY_SOURCE);
        environment.getPropertySources().remove(LogPresetSupport.JSON_RESOLVED_PROPERTY_SOURCE);
        if (LogPresetSupport.isBootstrapEnvironment(environment)) {
            return;
        }
        DefaultPropertiesPropertySource.moveToEnd(environment);
        String location = environment.getProperty(LogPresetSupport.LOGGING_CONFIG);
        Preset preset = LogPresetSupport.resolve(location);
        LogJsonProperties json = bindJson(environment);
        validateJson(json);
        if (LogPresetSupport.looksLikePreset(location)) {
            log.info(
                    "logging.config 未被识别为 Mimir 日志预设，四组缺省脱敏规则与预设参数未启用；请使用 classpath:"
                            + LogPresetSupport.CONTAINER_LOCATION
                            + " 或 classpath:"
                            + LogPresetSupport.SERVER_LOCATION);
        }
        ClassLoader classLoader =
                application != null
                        ? application.getClassLoader()
                        : ClassUtils.getDefaultClassLoader();
        if (!isLogbackBackend(classLoader)) {
            if (json.getFormat() == LogFormat.JSON) {
                throw new IllegalStateException(
                        FORMAT_PROPERTY + " 的 JSON 输出需要 container、server 预设及 Logback 后端");
            }
            if (preset != null) {
                throw new IllegalStateException("logging.config 选择的 Mimir 日志预设需要可用的 Logback 日志后端");
            }
            return;
        }
        validateJsonBackend(json, preset, classLoader);
        // 旧配置的早期规则沿用 2.3.0 语义（LoggerContext 属性/系统属性惰性读取，刷新时发布）
        Map<String, Object> presetKeys =
                preset == null ? Map.of() : resolvePreset(environment, preset, json);
        Map<String, Object> jsonKeys = jsonInternalKeys(json, environment);
        // 两组设置全部解析成功之后才写入，任一配置校验失败都不会留下半成品内部源。
        if (preset != null) {
            environment
                    .getPropertySources()
                    .addFirst(
                            new MapPropertySource(
                                    LogPresetSupport.RESOLVED_PROPERTY_SOURCE, presetKeys));
        }
        if (!jsonKeys.isEmpty()) {
            environment
                    .getPropertySources()
                    .addFirst(
                            new MapPropertySource(
                                    LogPresetSupport.JSON_RESOLVED_PROPERTY_SOURCE, jsonKeys));
        }
    }

    private static LogJsonProperties bindJson(ConfigurableEnvironment environment) {
        Binder binder = Binder.get(environment);
        LogJsonProperties properties = new LogJsonProperties();
        properties.setFormat(bindStrict(() -> bindFormat(binder), FORMAT_PROPERTY));
        properties.setEnvironment(
                bindStrict(
                        () -> binder.bind(ENVIRONMENT_PROPERTY, String.class).orElse(null),
                        ENVIRONMENT_PROPERTY));
        properties.setInstance(
                bindStrict(
                        () -> binder.bind(INSTANCE_PROPERTY, String.class).orElse(null),
                        INSTANCE_PROPERTY));
        properties
                .getJson()
                .setExcludeMdcKeys(
                        bindStrict(
                                () ->
                                        binder.bind(
                                                        EXCLUDE_MDC_KEYS_PROPERTY,
                                                        Bindable.listOf(String.class))
                                                .orElse(List.of()),
                                EXCLUDE_MDC_KEYS_PROPERTY));
        properties
                .getJson()
                .setFieldLengthLimits(
                        bindStrict(
                                () -> bindFieldLengthLimits(binder), FIELD_LENGTH_LIMITS_PROPERTY));
        return properties;
    }

    private static LogFormat bindFormat(Binder binder) {
        String configured =
                binder.bind(FORMAT_PROPERTY, String.class).map(String::trim).orElse(null);
        if (configured == null) {
            return LogFormat.TEXT;
        }
        if ("text".equalsIgnoreCase(configured)) {
            return LogFormat.TEXT;
        }
        if ("json".equalsIgnoreCase(configured)) {
            return LogFormat.JSON;
        }
        throw new IllegalStateException("属性 " + FORMAT_PROPERTY + " 仅允许 text 或 json");
    }

    /** 先保留数值原文再严格解析，避免 Binder 将 YAML 小数截断为整数。 */
    private static Map<String, Integer> bindFieldLengthLimits(Binder binder) {
        Map<String, String> raw =
                binder.bind(
                                FIELD_LENGTH_LIMITS_PROPERTY,
                                Bindable.mapOf(String.class, String.class))
                        .orElse(Map.of());
        Map<String, Integer> limits = new LinkedHashMap<>();
        raw.forEach((pointer, limit) -> limits.put(pointer, Integer.parseInt(limit)));
        return limits;
    }

    private static void validateJson(LogJsonProperties properties) {
        properties
                .getJson()
                .getFieldLengthLimits()
                .forEach(
                        (pointer, limit) -> {
                            if (pointer == null
                                    || pointer.isEmpty()
                                    || limit == null
                                    || limit < 1) {
                                throw invalidLimit();
                            }
                            validatePointer(pointer);
                        });
    }

    // 启动诊断统一为属性名和固定格式要求，刻意不传播解析器原因链。
    @SuppressWarnings("PMD.PreserveStackTrace")
    private static void validatePointer(String pointer) {
        try {
            JsonPointer.parse(pointer);
        } catch (IllegalArgumentException ex) {
            throw invalidLimit();
        }
    }

    private static IllegalStateException invalidLimit() {
        return new IllegalStateException(
                FIELD_LENGTH_LIMITS_PROPERTY + " 的键必须为非空 JSON Pointer，值必须为正整数；请用 [...] 方括号写法保留路径");
    }

    private static void validateJsonBackend(
            LogJsonProperties json, Preset preset, ClassLoader classLoader) {
        if (json.getFormat() != LogFormat.JSON) {
            return;
        }
        if (preset == null) {
            throw new IllegalStateException(
                    FORMAT_PROPERTY + "=json 仅适用于 container、server 预设；请改用预设或在自定义 XML 中引用公开编码器");
        }
        if (!ClassUtils.isPresent(JSON_ENCODER_CLASS, classLoader)
                || !ClassUtils.isPresent(JACKSON_MAPPER_CLASS, classLoader)) {
            throw new IllegalStateException(
                    FORMAT_PROPERTY
                            + " 启用 JSON 需声明 net.logstash.logback:logstash-logback-encoder 依赖");
        }
    }

    private static Map<String, Object> jsonInternalKeys(
            LogJsonProperties properties, ConfigurableEnvironment environment) {
        Map<String, Object> internal = new LinkedHashMap<>();
        String explicit = properties.getEnvironment();
        String[] profiles = environment.getActiveProfiles();
        String resolvedEnvironment =
                explicit == null || explicit.isBlank()
                        ? profiles.length == 1 ? profiles[0] : null
                        : explicit;
        putEncoded(internal, LogJsonKeys.INTERNAL_ENVIRONMENT, resolvedEnvironment);
        putEncoded(internal, LogJsonKeys.INTERNAL_INSTANCE, properties.getInstance());
        if (!properties.getJson().getExcludeMdcKeys().isEmpty()) {
            internal.put(
                    LogJsonKeys.INTERNAL_EXCLUDE_MDC_KEYS,
                    LogJsonSettingsCodec.encodeList(properties.getJson().getExcludeMdcKeys()));
        }
        if (!properties.getJson().getFieldLengthLimits().isEmpty()) {
            internal.put(
                    LogJsonKeys.INTERNAL_FIELD_LENGTH_LIMITS,
                    LogJsonSettingsCodec.encodeLimits(properties.getJson().getFieldLengthLimits()));
        }
        return internal;
    }

    private static void putEncoded(Map<String, Object> internal, String key, String value) {
        if (value != null && !value.isBlank()) {
            internal.put(key, LogJsonSettingsCodec.encode(value));
        }
    }

    /** 校验全部先于写入：失败时不改变 Environment 与转换器状态 */
    private static Map<String, Object> resolvePreset(
            ConfigurableEnvironment environment, Preset preset, LogJsonProperties json) {
        Binder binder = Binder.get(environment);
        List<String> enabled =
                bindStrict(
                        () -> LogPresetSupport.selectEnabledPatterns(environment, preset),
                        LogPresetSupport.ENABLED_PATTERNS);
        LogMaskProperties mask =
                bindStrict(
                        () -> binder.bindOrCreate("mimir.boot.log.mask", LogMaskProperties.class),
                        "mimir.boot.log.mask");
        BindResult<String> replacement =
                bindStrict(
                        () -> binder.bind(LogPresetSupport.REPLACEMENT, String.class),
                        LogPresetSupport.REPLACEMENT);
        LogPresetSupport.validateStrict(
                enabled, mask.getCustomPatterns(), replacement.orElse(null));
        Map<String, Object> internal = new LinkedHashMap<>();
        if (preset == Preset.SERVER) {
            LogPresetProperties presetProperties =
                    bindStrict(
                            () ->
                                    binder.bindOrCreate(
                                            "mimir.boot.log",
                                            Bindable.of(LogPresetProperties.class)),
                            "mimir.boot.log.file / mimir.boot.log.async");
            presetProperties.validate();
            internal.putAll(presetProperties.toInternalProperties());
        }
        putMaskKeys(internal, binder, enabled, mask, replacement);
        internal.put(LogJsonKeys.INTERNAL_FORMAT, json.getFormat().name().toLowerCase(Locale.ROOT));
        if (json.getFormat() == LogFormat.JSON) {
            internal.put(LogJsonKeys.INTERNAL_CAPTURE_ACTIVE, "true");
        }
        return internal;
    }

    /** 仅在用户对应属性已绑定时写入；空列表写空字符串，避免 springProperty 回落到四组 defaultValue */
    private static void putMaskKeys(
            Map<String, Object> internal,
            Binder binder,
            List<String> enabled,
            LogMaskProperties mask,
            BindResult<String> replacement) {
        if (binder.bind(LogPresetSupport.ENABLED_PATTERNS, Bindable.listOf(String.class))
                .isBound()) {
            internal.put(LogPresetSupport.INTERNAL_ENABLED_PATTERNS, encode(enabled));
        }
        if (binder.bind(LogPresetSupport.CUSTOM_PATTERNS, Bindable.listOf(String.class))
                .isBound()) {
            internal.put(
                    LogPresetSupport.INTERNAL_CUSTOM_PATTERNS, encode(mask.getCustomPatterns()));
        }
        if (replacement.isBound()) {
            internal.put(LogPresetSupport.INTERNAL_REPLACEMENT, replacement.get());
        }
    }

    private static String encode(List<String> values) {
        String encoded = MaskListCodec.join(values);
        return encoded == null ? "" : encoded;
    }

    // 绑定与数值解析异常均可能含配置原文；启动诊断只暴露属性名，不能保留其原因链。
    @SuppressWarnings("PMD.PreserveStackTrace")
    private static <T> T bindStrict(java.util.function.Supplier<T> binding, String property) {
        try {
            return binding.get();
        } catch (BindException | NumberFormatException ex) {
            throw new IllegalStateException("属性 " + property + " 无法绑定，请检查其类型与格式");
        }
    }

    private static boolean isLogbackBackend(ClassLoader classLoader) {
        if (!ClassUtils.isPresent(LOGBACK_CONTEXT_CLASS, classLoader)
                || !ClassUtils.isPresent(LOGBACK_CONVERTER_CLASS, classLoader)) {
            return false;
        }
        Class<?> type = LoggingSystem.get(classLoader).getClass();
        while (type != null) {
            if (LOGBACK_LOGGING_SYSTEM.equals(type.getName())) {
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }
}
