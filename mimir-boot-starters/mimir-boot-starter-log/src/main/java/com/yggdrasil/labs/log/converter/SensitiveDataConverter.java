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

    private record MaskConfigurationSnapshot(List<Pattern> patterns,
                                             List<String> keyValueFieldNames,
                                             String replacement) {
    }

    private record SensitiveFieldValue(int valueStart, int valueEnd) {
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
        synchronized (CONFIGURATION_LOCK) {
            configuration.set(buildConfiguration(enabledPatternNames, customPatternExpressions, replacement));
        }
    }

    /**
     * 重新从 Logback 或系统属性加载配置，保留既有动态刷新入口。
     */
    public static void reloadConfig() {
        synchronized (CONFIGURATION_LOCK) {
            configuration.set(null);
        }
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
        List<String> keyValueFieldNames = compilePresetPatterns(patterns, enabledPatternNames);
        compilePatterns(patterns, customPatternExpressions, "Invalid custom mask pattern: ");
        synchronized (CONFIGURATION_LOCK) {
            compilePatterns(patterns, PROGRAMMATIC_PATTERNS, "Invalid programmatic mask pattern: ");
        }
        String resolvedReplacement = replacement == null || replacement.isEmpty()
                ? DEFAULT_REPLACEMENT
                : replacement;
        return new MaskConfigurationSnapshot(List.copyOf(patterns), keyValueFieldNames, resolvedReplacement);
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
            if (pattern != SensitiveDataPattern.PASSWORD
                    && pattern != SensitiveDataPattern.TOKEN
                    && pattern != SensitiveDataPattern.SECRET) {
                compilePatterns(target, List.of(pattern.getPattern()), "Invalid preset mask pattern: ");
            }
        }
        return SensitiveDataPattern.keyValueFieldNames(selectedPatterns);
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
        String result = maskKeyValueFields(message, snapshot.keyValueFieldNames(), snapshot.replacement());
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

    private static String maskKeyValueFields(String message, List<String> fieldNames, String replacement) {
        if (fieldNames.isEmpty()) {
            return message;
        }
        StringBuilder masked = null;
        int copiedUntil = 0;
        int index = 0;
        while (index < message.length()) {
            SensitiveFieldValue fieldValue = findSensitiveFieldValue(message, index, fieldNames);
            if (fieldValue == null) {
                index++;
            } else {
                if (masked == null) {
                    masked = new StringBuilder(message.length());
                }
                masked.append(message, copiedUntil, fieldValue.valueStart());
                appendMaskedFieldValue(masked, message, fieldValue.valueStart(), replacement);
                copiedUntil = fieldValue.valueEnd();
                index = fieldValue.valueEnd();
            }
        }
        return masked == null ? message : masked.append(message, copiedUntil, message.length()).toString();
    }

    private static SensitiveFieldValue findSensitiveFieldValue(String message, int index,
                                                               List<String> fieldNames) {
        if (!isPotentialFieldInitial(message.charAt(index))) {
            return null;
        }
        String fieldName = matchingFieldName(message, index, fieldNames);
        if (fieldName == null) {
            return null;
        }
        int cursor = index + fieldName.length();
        if (cursor < message.length() && isQuote(message.charAt(cursor))) {
            cursor++;
        }
        while (cursor < message.length() && Character.isWhitespace(message.charAt(cursor))) {
            cursor++;
        }
        if (cursor >= message.length() || (message.charAt(cursor) != '=' && message.charAt(cursor) != ':')) {
            return null;
        }
        cursor++;
        while (cursor < message.length() && Character.isWhitespace(message.charAt(cursor))) {
            cursor++;
        }
        int valueStart = cursor;
        int valueEnd = valueEnd(message, valueStart);
        return valueEnd == valueStart ? null : new SensitiveFieldValue(valueStart, valueEnd);
    }

    private static void appendMaskedFieldValue(StringBuilder masked, String message, int valueStart,
                                               String replacement) {
        if (isQuote(message.charAt(valueStart))) {
            masked.append(message.charAt(valueStart)).append(replacement).append(message.charAt(valueStart));
        } else {
            masked.append(replacement);
        }
    }

    private static String matchingFieldName(String message, int index, List<String> fieldNames) {
        char initial = message.charAt(index);
        for (String fieldName : fieldNames) {
            if (sameAsciiCase(initial, fieldName.charAt(0))
                    && message.regionMatches(true, index, fieldName, 0, fieldName.length())) {
                return fieldName;
            }
        }
        return null;
    }

    private static boolean isPotentialFieldInitial(char value) {
        return switch (value) {
            case 'a', 'A', 'p', 'P', 's', 'S', 't', 'T', '%', '密', '私' -> true;
            default -> false;
        };
    }

    private static boolean sameAsciiCase(char left, char right) {
        return left == right || ((left ^ 32) == right && isAsciiLetter(left));
    }

    private static boolean isAsciiLetter(char value) {
        return value >= 'A' && value <= 'Z' || value >= 'a' && value <= 'z';
    }

    private static int valueEnd(String message, int valueStart) {
        if (valueStart >= message.length()) {
            return valueStart;
        }
        char firstCharacter = message.charAt(valueStart);
        if (isQuote(firstCharacter)) {
            int slashCount = 0;
            for (int cursor = valueStart + 1; cursor < message.length(); cursor++) {
                char current = message.charAt(cursor);
                if (current == '\\') {
                    slashCount++;
                } else {
                    if (current == firstCharacter && slashCount % 2 == 0) {
                        return cursor + 1;
                    }
                    slashCount = 0;
                }
            }
            return message.length();
        }
        int cursor = valueStart;
        while (cursor < message.length()
                && message.charAt(cursor) != ','
                && message.charAt(cursor) != '}'
                && message.charAt(cursor) != ']'
                && !Character.isWhitespace(message.charAt(cursor))) {
            cursor++;
        }
        return cursor;
    }

    private static boolean isQuote(char value) {
        return value == '\"' || value == '\'';
    }

    private static String maskValue(Matcher matcher, String replacement) {
        String matched = matcher.group();
        String capturedPrefix = matcher.groupCount() > 0 ? matcher.group(1) : null;
        if (capturedPrefix != null && endsWithKeyValueSeparator(capturedPrefix)) {
            String suffix = matched.substring(capturedPrefix.length()).stripLeading();
            String quote = suffix.startsWith("\"") || suffix.startsWith("'") ? suffix.substring(0, 1) : "";
            return capturedPrefix + quote + replacement + quote;
        }
        int equalsIndex = matched.indexOf('=');
        int separatorIndex = equalsIndex < 0 ? matched.indexOf(':') : equalsIndex;
        if (separatorIndex < 0) {
            return replacement;
        }
        String prefix = matched.substring(0, separatorIndex + 1);
        String suffix = matched.substring(separatorIndex + 1);
        String quote = suffix.startsWith("\"") || suffix.startsWith("'") ? suffix.substring(0, 1) : "";
        return prefix + quote + replacement + quote;
    }

    private static boolean endsWithKeyValueSeparator(String value) {
        String trimmed = value.stripTrailing();
        return trimmed.endsWith("=") || trimmed.endsWith(":");
    }
}
