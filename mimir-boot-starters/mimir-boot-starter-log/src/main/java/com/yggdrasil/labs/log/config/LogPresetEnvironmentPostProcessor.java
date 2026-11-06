package com.yggdrasil.labs.log.config;

import java.util.LinkedHashMap;
import java.util.List;
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

/**
 * 日志预设早期处理器
 *
 * <p>在全部 EnvironmentPostProcessor 之后、Boot 正式初始化日志之前，识别 {@code logging.config} 选择的 Mimir
 * 预设，严格校验并生成内部属性源：服务器预设的规范化数值与已配置的脱敏规则，由预设 XML 的 springProperty 写入 LoggerContext（D-101）。
 * 不触碰转换器静态状态。未选预设且无可用 Logback 后端时无状态退出；显式选择预设但后端不兼容时启动失败。Spring Cloud legacy bootstrap
 * 应用只移除残留内部源（D-100）。
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
        if (LogPresetSupport.isBootstrapEnvironment(environment)) {
            return;
        }
        DefaultPropertiesPropertySource.moveToEnd(environment);
        String location = environment.getProperty(LogPresetSupport.LOGGING_CONFIG);
        Preset preset = LogPresetSupport.resolve(location);
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
            if (preset != null) {
                throw new IllegalStateException("logging.config 选择的 Mimir 日志预设需要可用的 Logback 日志后端");
            }
            return;
        }
        // 旧配置的早期规则沿用 2.3.0 语义（LoggerContext 属性/系统属性惰性读取，刷新时发布）
        if (preset != null) {
            resolvePreset(environment, preset);
        }
    }

    /** 校验全部先于写入：失败时不改变 Environment 与转换器状态 */
    private static void resolvePreset(ConfigurableEnvironment environment, Preset preset) {
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
        // 选中预设时内部源必定存在（可为空），供刷新守卫识别预设应用的父上下文
        environment
                .getPropertySources()
                .addFirst(
                        new MapPropertySource(LogPresetSupport.RESOLVED_PROPERTY_SOURCE, internal));
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

    // BindException 的原因链可能含配置原文；启动诊断只暴露属性名，不能保留该原因链。
    @SuppressWarnings("PMD.PreserveStackTrace")
    private static <T> T bindStrict(java.util.function.Supplier<T> binding, String property) {
        try {
            return binding.get();
        } catch (BindException ex) {
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
