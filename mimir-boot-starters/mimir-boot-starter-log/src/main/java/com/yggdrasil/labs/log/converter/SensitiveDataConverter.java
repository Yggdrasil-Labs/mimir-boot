package com.yggdrasil.labs.log.converter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yggdrasil.labs.common.constant.CommonConstants;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Context;

/**
 * 敏感信息脱敏转换器。
 *
 * <p>配置以不可变快照整体发布，因此一次转换只会使用同一代规则和替换字符。
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
public class SensitiveDataConverter extends ClassicConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(SensitiveDataConverter.class);

    public static final String MASK_ENABLED_PATTERNS_PROPERTY =
            "mimir.boot.log.mask.enabledPatterns";
    public static final String MASK_CUSTOM_PATTERNS_PROPERTY = "mimir.boot.log.mask.customPatterns";
    public static final String MASK_REPLACEMENT_PROPERTY = "mimir.boot.log.mask.replacement";

    private static final String DEFAULT_REPLACEMENT = CommonConstants.MASKED;
    private static final Object CONFIGURATION_LOCK = new Object();
    private static final List<String> PROGRAMMATIC_PATTERNS = new ArrayList<>();
    private static final AtomicReference<Context> CONFIGURATION_CONTEXT = new AtomicReference<>();
    private static final AtomicReference<MaskConfigurationSnapshot> CONFIGURATION =
            new AtomicReference<>();

    private record MaskConfigurationSnapshot(
            List<Pattern> patterns, KeyValueMasker keyValueFieldNames, String replacement) {}

    // 只有当前 Logback Context 实例可以接管全局快照，不能按内容相等判断归属。
    @SuppressWarnings("PMD.CompareObjectsWithEquals")
    @Override
    public void start() {
        // 绑定本次真实 Context：全局引用为空时沿用首次绑定，否则只接受 SLF4J 当前的 LoggerContext，
        // 避免第三方或临时创建的 LoggerContext 抢占；未设置 Context 的实例不改变引用，也不清空已发布快照
        Context context = getContext();
        if (context != null
                && (CONFIGURATION_CONTEXT.get() == null
                        || context == LoggerFactory.getILoggerFactory())) {
            synchronized (CONFIGURATION_LOCK) {
                CONFIGURATION_CONTEXT.set(context);
                // 预设 XML 经 springProperty 写入 mask 属性：本次配置携带规则，切换到新规则代次；
                // 不带这些属性的旧 XML 重新加载保留已发布快照（与 2.3.0 一致）
                if (hasMaskProperties(context)) {
                    CONFIGURATION.set(null);
                }
            }
        }
        super.start();
    }

    private static boolean hasMaskProperties(Context context) {
        return context.getProperty(MASK_ENABLED_PATTERNS_PROPERTY) != null
                || context.getProperty(MASK_CUSTOM_PATTERNS_PROPERTY) != null
                || context.getProperty(MASK_REPLACEMENT_PROPERTY) != null;
    }

    @Override
    public String convert(ILoggingEvent event) {
        String message = event.getFormattedMessage();
        return message == null || message.isEmpty()
                ? message
                : maskSensitiveData(message, currentConfiguration());
    }

    /** 原子发布完整配置。规则编译完成前不会影响正在输出的日志。 */
    public static void publishConfiguration(
            List<String> enabledPatternNames,
            List<String> customPatternExpressions,
            String replacement) {
        synchronized (CONFIGURATION_LOCK) {
            CONFIGURATION.set(
                    buildConfiguration(enabledPatternNames, customPatternExpressions, replacement));
        }
    }

    /** 重新从 Logback 或系统属性加载配置，保留既有动态刷新入口。 */
    public static void reloadConfig() {
        synchronized (CONFIGURATION_LOCK) {
            CONFIGURATION.set(null);
        }
    }

    /** 登记程序化正则，并使后续转换重新构建规则快照。 */
    public static void addCustomPattern(String pattern) {
        synchronized (CONFIGURATION_LOCK) {
            PROGRAMMATIC_PATTERNS.add(pattern);
        }
        reloadConfig();
    }

    /** 清空程序化正则，并使后续转换重新构建规则快照。 */
    public static void clearCustomPatterns() {
        synchronized (CONFIGURATION_LOCK) {
            PROGRAMMATIC_PATTERNS.clear();
        }
        reloadConfig();
    }

    /** 返回全部内置脱敏规则名称。 */
    public static List<String> getAllPresetPatternNames() {
        List<String> names = new ArrayList<>();
        for (SensitiveDataPattern pattern : SensitiveDataPattern.values()) {
            names.add(pattern.getName());
        }
        return names;
    }

    /** 测试钩子：清除快照并恢复全局 Context 引用；生产代码不调用 */
    static void resetForTest(Context context) {
        synchronized (CONFIGURATION_LOCK) {
            CONFIGURATION.set(null);
            CONFIGURATION_CONTEXT.set(context);
        }
    }

    /** 对敏感信息进行脱敏。 */
    public String maskSensitiveData(String message) {
        return message == null || message.isEmpty()
                ? message
                : maskSensitiveData(message, currentConfiguration());
    }

    private static MaskConfigurationSnapshot currentConfiguration() {
        MaskConfigurationSnapshot current = CONFIGURATION.get();
        if (current != null) {
            return current;
        }
        synchronized (CONFIGURATION_LOCK) {
            current = CONFIGURATION.get();
            if (current == null) {
                current =
                        buildConfiguration(
                                readConfigurationAsList(MASK_ENABLED_PATTERNS_PROPERTY),
                                readConfigurationAsList(MASK_CUSTOM_PATTERNS_PROPERTY),
                                readConfiguration(MASK_REPLACEMENT_PROPERTY));
                CONFIGURATION.set(current);
            }
            return current;
        }
    }

    private static MaskConfigurationSnapshot buildConfiguration(
            List<String> enabledPatternNames,
            List<String> customPatternExpressions,
            String replacement) {
        List<Pattern> patterns = new ArrayList<>();
        List<String> keyValueFieldNames = compilePresetPatterns(patterns, enabledPatternNames);
        compilePatterns(patterns, customPatternExpressions, "Invalid custom mask pattern: ");
        synchronized (CONFIGURATION_LOCK) {
            compilePatterns(patterns, PROGRAMMATIC_PATTERNS, "Invalid programmatic mask pattern: ");
        }
        String resolvedReplacement =
                replacement == null || replacement.isEmpty() ? DEFAULT_REPLACEMENT : replacement;
        return new MaskConfigurationSnapshot(
                List.copyOf(patterns), new KeyValueMasker(keyValueFieldNames), resolvedReplacement);
    }

    private static List<String> compilePresetPatterns(List<Pattern> target, List<String> names) {
        if (names == null) {
            return List.of();
        }
        List<SensitiveDataPattern> selectedPatterns = new ArrayList<>();
        for (String name : names) {
            if (name == null) {
                continue;
            }
            SensitiveDataPattern pattern = SensitiveDataPattern.fromName(name.trim());
            if (pattern != null) {
                selectedPatterns.add(pattern);
            }
        }
        for (SensitiveDataPattern pattern : selectedPatterns) {
            if (SensitiveDataPattern.keyValueFieldNames(List.of(pattern)).isEmpty()) {
                compilePatterns(
                        target, List.of(pattern.getPattern()), "Invalid preset mask pattern: ");
            }
        }
        return SensitiveDataPattern.keyValueFieldNames(selectedPatterns);
    }

    private static void compilePatterns(
            List<Pattern> target, List<String> expressions, String errorPrefix) {
        if (expressions == null) {
            return;
        }
        for (String expression : expressions) {
            if (expression == null || expression.isBlank()) {
                continue;
            }
            try {
                target.add(Pattern.compile(expression.trim()));
            } catch (RuntimeException ignored) {
                LOGGER.warn("{}invalid expression ignored", errorPrefix);
            }
        }
    }

    private static List<String> readConfigurationAsList(String key) {
        String value = readConfiguration(key);
        return value == null || value.isEmpty() ? List.of() : MaskListCodec.split(value);
    }

    private static String readConfiguration(String key) {
        Context context = CONFIGURATION_CONTEXT.get();
        String value = context == null ? null : context.getProperty(key);
        return value == null || value.isEmpty() ? System.getProperty(key) : value;
    }

    private static String maskSensitiveData(String message, MaskConfigurationSnapshot snapshot) {
        String result = snapshot.keyValueFieldNames().mask(message, snapshot.replacement());
        for (Pattern pattern : snapshot.patterns()) {
            Matcher matcher = pattern.matcher(result);
            if (!matcher.find()) {
                continue;
            }
            StringBuilder masked = new StringBuilder(result.length());
            int lastMatchEnd = 0;
            do {
                masked.append(result, lastMatchEnd, matcher.start());
                masked.append(maskValue(matcher, snapshot.replacement()));
                lastMatchEnd = matcher.end();
            } while (matcher.find());
            result = masked.append(result, lastMatchEnd, result.length()).toString();
        }
        return result;
    }

    private static String maskValue(Matcher matcher, String replacement) {
        String matched = matcher.group();
        String capturedPrefix = matcher.groupCount() > 0 ? matcher.group(1) : null;
        if (capturedPrefix != null && endsWithKeyValueSeparator(capturedPrefix)) {
            String suffix = matched.substring(capturedPrefix.length()).stripLeading();
            String quote =
                    suffix.startsWith("\"") || suffix.startsWith("'") ? suffix.substring(0, 1) : "";
            return capturedPrefix + quote + replacement + quote;
        }
        int equalsIndex = matched.indexOf('=');
        int separatorIndex = equalsIndex < 0 ? matched.indexOf(':') : equalsIndex;
        if (separatorIndex < 0) {
            return replacement;
        }
        String prefix = matched.substring(0, separatorIndex + 1);
        String suffix = matched.substring(separatorIndex + 1);
        String quote =
                suffix.startsWith("\"") || suffix.startsWith("'") ? suffix.substring(0, 1) : "";
        return prefix + quote + replacement + quote;
    }

    private static boolean endsWithKeyValueSeparator(String value) {
        String trimmed = value.stripTrailing();
        return trimmed.endsWith("=") || trimmed.endsWith(":");
    }
}
