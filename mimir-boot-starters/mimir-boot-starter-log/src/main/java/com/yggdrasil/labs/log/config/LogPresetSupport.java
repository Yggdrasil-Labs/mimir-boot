package com.yggdrasil.labs.log.config;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

import com.yggdrasil.labs.log.converter.SensitiveDataPattern;

/**
 * 日志预设识别与脱敏规则选择（内部实现）
 *
 * <p>不引用 Logback 类型，可在后端检查前安全加载。早期发布与根上下文刷新共用此处的规则选择。
 */
final class LogPresetSupport {

    static final String LOGGING_CONFIG = "logging.config";
    static final String CONTAINER_LOCATION = "com/yggdrasil/labs/log/logback/container.xml";
    static final String SERVER_LOCATION = "com/yggdrasil/labs/log/logback/server.xml";
    static final String BOOTSTRAP_PROPERTY_SOURCE = "bootstrap";
    static final String RESOLVED_PROPERTY_SOURCE = "mimirLogPresetResolved";
    static final String ENABLED_PATTERNS = "mimir.boot.log.mask.enabled-patterns";
    static final String CUSTOM_PATTERNS = "mimir.boot.log.mask.custom-patterns";
    static final String REPLACEMENT = "mimir.boot.log.mask.replacement";

    /** 预设 XML 经 springProperty 读取的 mask 内部键（D-101）；仅在用户对应属性已绑定时写入，未写入时取 XML defaultValue */
    static final String INTERNAL_ENABLED_PATTERNS =
            LogPresetProperties.INTERNAL_PREFIX + "mask.enabled-patterns";

    static final String INTERNAL_CUSTOM_PATTERNS =
            LogPresetProperties.INTERNAL_PREFIX + "mask.custom-patterns";
    static final String INTERNAL_REPLACEMENT =
            LogPresetProperties.INTERNAL_PREFIX + "mask.replacement";
    static final List<String> PRESET_DEFAULT_PATTERNS =
            List.of("password", "token", "secret", "api_key");

    private static final String CLASSPATH_PREFIX = "classpath:";

    enum Preset {
        CONTAINER,
        SERVER
    }

    private LogPresetSupport() {}

    /** Spring Cloud legacy bootstrap 应用的 Environment 含名为 bootstrap 的属性源（D-100） */
    static boolean isBootstrapEnvironment(Environment environment) {
        return environment instanceof ConfigurableEnvironment configurable
                && configurable.getPropertySources().contains(BOOTSTRAP_PROPERTY_SOURCE);
    }

    static Preset resolve(Environment environment) {
        return resolve(environment.getProperty(LOGGING_CONFIG));
    }

    /** 仅识别大小写敏感的 classpath: 前缀（允许其后一个 /）及首尾空白，其余写法不视为预设 */
    static Preset resolve(String location) {
        if (location == null) {
            return null;
        }
        String value = location.strip();
        if (!value.startsWith(CLASSPATH_PREFIX)) {
            return null;
        }
        String path = value.substring(CLASSPATH_PREFIX.length());
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (CONTAINER_LOCATION.equals(path)) {
            return Preset.CONTAINER;
        }
        return SERVER_LOCATION.equals(path) ? Preset.SERVER : null;
    }

    /** 未被识别但以预设文件位置结尾的写法，用于输出一次未识别提示 */
    static boolean looksLikePreset(String location) {
        if (location == null || resolve(location) != null) {
            return false;
        }
        String value = location.strip();
        return value.endsWith(CONTAINER_LOCATION) || value.endsWith(SERVER_LOCATION);
    }

    /**
     * 选择有效启用列表：已绑定（含空列表、空字符串、索引与 relaxed 写法）时原样使用；未绑定时新预设取四组缺省，否则为空
     *
     * @throws org.springframework.boot.context.properties.bind.BindException 绑定失败
     */
    static List<String> selectEnabledPatterns(Environment environment, Preset preset) {
        BindResult<List<String>> bound =
                Binder.get(environment).bind(ENABLED_PATTERNS, Bindable.listOf(String.class));
        if (bound.isBound()) {
            return bound.get();
        }
        return preset != null ? PRESET_DEFAULT_PATTERNS : List.of();
    }

    /**
     * 新预设严格校验：未知规则名、非法正则，以及经 Binder 宽松解析后仍含 {@code ${} 的自定义正则与替换串（springProperty
     * 读取内部键时会严格解析占位符并回显原文）启动失败；诊断只含属性名与序号，不回显值
     */
    static void validateStrict(
            List<String> enabledPatterns, List<String> customPatterns, String replacement) {
        if (replacement != null && replacement.contains("${")) {
            throw new IllegalStateException(REPLACEMENT + " 含无法解析的占位符");
        }
        validateEnabledPatterns(enabledPatterns);
        validateCustomPatterns(customPatterns);
    }

    private static void validateEnabledPatterns(List<String> enabledPatterns) {
        for (int i = 0; i < enabledPatterns.size(); i++) {
            String name = enabledPatterns.get(i);
            if (name != null
                    && !name.isBlank()
                    && SensitiveDataPattern.fromName(name.trim()) == null) {
                throw new IllegalStateException(ENABLED_PATTERNS + "[" + i + "] 不是已知的预置脱敏规则名");
            }
        }
    }

    private static void validateCustomPatterns(List<String> customPatterns) {
        if (customPatterns == null) {
            return;
        }
        for (int i = 0; i < customPatterns.size(); i++) {
            String expression = customPatterns.get(i);
            if (expression == null || expression.isBlank()) {
                continue;
            }
            if (expression.contains("${")) {
                throw new IllegalStateException(CUSTOM_PATTERNS + "[" + i + "] 含无法解析的占位符");
            }
            validateCustomPattern(expression, i);
        }
    }

    // PatternSyntaxException 含完整正则，可能暴露敏感配置；此处有意只报告属性与序号。
    @SuppressWarnings("PMD.PreserveStackTrace")
    private static void validateCustomPattern(String expression, int index) {
        try {
            Pattern.compile(expression.trim());
        } catch (PatternSyntaxException ex) {
            throw new IllegalStateException(CUSTOM_PATTERNS + "[" + index + "] 不是合法的正则表达式");
        }
    }
}
