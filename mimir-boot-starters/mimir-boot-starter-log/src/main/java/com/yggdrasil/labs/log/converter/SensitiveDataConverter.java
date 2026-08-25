package com.yggdrasil.labs.log.converter;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Context;
import com.yggdrasil.labs.common.constant.CommonConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 敏感信息脱敏转换器。
 *
 * <p>配置以不可变快照整体发布，因此一次转换只会使用同一代规则和替换字符。</p>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
public class SensitiveDataConverter extends ClassicConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(SensitiveDataConverter.class);

    public static final String MASK_ENABLED_PATTERNS_PROPERTY = "mimir.boot.log.mask.enabledPatterns";
    public static final String MASK_CUSTOM_PATTERNS_PROPERTY = "mimir.boot.log.mask.customPatterns";
    public static final String MASK_REPLACEMENT_PROPERTY = "mimir.boot.log.mask.replacement";

    private static final String DEFAULT_REPLACEMENT = CommonConstants.MASKED;
    private static final Object CONFIGURATION_LOCK = new Object();
    private static final List<String> PROGRAMMATIC_PATTERNS = new ArrayList<>();
    private static final AtomicReference<Context> CONFIGURATION_CONTEXT = new AtomicReference<>();
    private static final AtomicReference<MaskConfigurationSnapshot> configuration = new AtomicReference<>();

    private record MaskConfigurationSnapshot(List<Pattern> patterns, String replacement) {
    }

    @Override
    public void start() {
        CONFIGURATION_CONTEXT.compareAndSet(null, getContext());
        super.start();
    }

    @Override
    public String convert(ILoggingEvent event) {
        String message = event.getFormattedMessage();
        return message == null || message.isEmpty()
                ? message
                : maskSensitiveData(message, currentConfiguration());
    }

    /**
     * 原子发布完整配置。规则编译完成前不会影响正在输出的日志。
     */
    public static void publishConfiguration(List<String> enabledPatternNames,
                                            List<String> customPatternExpressions,
                                            String replacement) {
        configuration.set(buildConfiguration(enabledPatternNames, customPatternExpressions, replacement));
    }

    /**
     * 重新从 Logback 或系统属性加载配置，保留既有动态刷新入口。
     */
    public static void reloadConfig() {
        configuration.set(null);
    }

    public static void addCustomPattern(String pattern) {
        synchronized (CONFIGURATION_LOCK) {
            PROGRAMMATIC_PATTERNS.add(pattern);
        }
        reloadConfig();
    }

    public static void clearCustomPatterns() {
        synchronized (CONFIGURATION_LOCK) {
            PROGRAMMATIC_PATTERNS.clear();
        }
        reloadConfig();
    }

    public static List<String> getAllPresetPatternNames() {
        List<String> names = new ArrayList<>();
        for (SensitiveDataPattern pattern : SensitiveDataPattern.values()) {
            names.add(pattern.getName());
        }
        return names;
    }

    /**
     * 对敏感信息进行脱敏。
     */
    public String maskSensitiveData(String message) {
        return message == null || message.isEmpty()
                ? message
                : maskSensitiveData(message, currentConfiguration());
    }

    private static MaskConfigurationSnapshot currentConfiguration() {
        MaskConfigurationSnapshot current = configuration.get();
        if (current != null) {
            return current;
        }
        synchronized (CONFIGURATION_LOCK) {
            current = configuration.get();
            if (current == null) {
                current = buildConfiguration(
                        readConfigurationAsList(MASK_ENABLED_PATTERNS_PROPERTY),
                        readConfigurationAsList(MASK_CUSTOM_PATTERNS_PROPERTY),
                        readConfiguration(MASK_REPLACEMENT_PROPERTY));
                configuration.set(current);
            }
            return current;
        }
    }

    private static MaskConfigurationSnapshot buildConfiguration(List<String> enabledPatternNames,
                                                                  List<String> customPatternExpressions,
                                                                  String replacement) {
        List<Pattern> patterns = new ArrayList<>();
        compilePresetPatterns(patterns, enabledPatternNames);
        compilePatterns(patterns, customPatternExpressions, "Invalid custom mask pattern: ");
        synchronized (CONFIGURATION_LOCK) {
            compilePatterns(patterns, PROGRAMMATIC_PATTERNS, "Invalid programmatic mask pattern: ");
        }
        String resolvedReplacement = replacement == null || replacement.isEmpty()
                ? DEFAULT_REPLACEMENT
                : replacement;
        return new MaskConfigurationSnapshot(List.copyOf(patterns), resolvedReplacement);
    }

    private static void compilePresetPatterns(List<Pattern> target, List<String> names) {
        if (names == null) {
            return;
        }
        for (String name : names) {
            if (name == null) {
                continue;
            }
            SensitiveDataPattern pattern = SensitiveDataPattern.fromName(name.trim());
            if (pattern != null) {
                compilePatterns(target, List.of(pattern.getPattern()), "Invalid preset mask pattern: ");
            }
        }
    }

    private static void compilePatterns(List<Pattern> target, List<String> expressions, String errorPrefix) {
        if (expressions == null) {
            return;
        }
        for (String expression : expressions) {
            if (expression == null || expression.isBlank()) {
                continue;
            }
            try {
                target.add(Pattern.compile(expression.trim()));
            } catch (RuntimeException exception) {
                LOGGER.warn("{}{}", errorPrefix, expression, exception);
            }
        }
    }

    private static List<String> readConfigurationAsList(String key) {
        String value = readConfiguration(key);
        return value == null || value.isEmpty() ? List.of() : List.of(value.split(","));
    }

    private static String readConfiguration(String key) {
        Context context = CONFIGURATION_CONTEXT.get();
        String value = context == null ? null : context.getProperty(key);
        return value == null || value.isEmpty() ? System.getProperty(key) : value;
    }

    private static String maskSensitiveData(String message, MaskConfigurationSnapshot snapshot) {
        String result = message;
        for (Pattern pattern : snapshot.patterns()) {
            Matcher matcher = pattern.matcher(result);
            StringBuffer buffer = new StringBuffer();
            while (matcher.find()) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(maskValue(matcher.group(), snapshot.replacement())));
            }
            if (buffer.length() > 0) {
                matcher.appendTail(buffer);
                result = buffer.toString();
            }
        }
        return result;
    }

    private static String maskValue(String matched, String replacement) {
        int separatorIndex = matched.indexOf('=');
        if (separatorIndex < 0) {
            separatorIndex = matched.indexOf(':');
        }
        if (separatorIndex < 0) {
            return replacement;
        }
        String prefix = matched.substring(0, separatorIndex + 1);
        String suffix = matched.substring(separatorIndex + 1);
        String quote = suffix.startsWith("\"") || suffix.startsWith("'") ? suffix.substring(0, 1) : "";
        return prefix + quote + replacement + quote;
    }
}
