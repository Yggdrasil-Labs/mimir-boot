package com.yggdrasil.labs.log.converter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private static final AtomicReference<MaskConfigurationSnapshot> configuration =
            new AtomicReference<>();

    private record MaskConfigurationSnapshot(
            List<Pattern> patterns, FieldNameIndex keyValueFieldNames, String replacement) {}

    /** 字段名按首字符分组的查找表：候选保持原长度降序；ASCII 字母同时登记大小写，其余字符只登记原字符，与逐个比较首字符的语义一致。 */
    private static final class FieldNameIndex {

        private final String[][] ascii = new String[128][];

        /** 非 ASCII 首字符按升序排列，配合二分查找，避免查找时装箱 */
        private final char[] otherInitials;

        private final String[][] otherCandidates;
        private final int maxLength;
        private final boolean empty;

        private FieldNameIndex(List<String> orderedNames) {
            Map<Character, List<String>> grouped = new LinkedHashMap<>();
            int longest = 0;
            for (String name : orderedNames) {
                char first = name.charAt(0);
                grouped.computeIfAbsent(first, key -> new ArrayList<>()).add(name);
                if (isAsciiLetter(first)) {
                    grouped.computeIfAbsent((char) (first ^ 32), key -> new ArrayList<>())
                            .add(name);
                }
                longest = Math.max(longest, name.length());
            }
            List<Character> others = new ArrayList<>();
            for (Map.Entry<Character, List<String>> entry : grouped.entrySet()) {
                if (entry.getKey() < 128) {
                    ascii[entry.getKey()] = entry.getValue().toArray(String[]::new);
                } else {
                    others.add(entry.getKey());
                }
            }
            Collections.sort(others);
            this.otherInitials = new char[others.size()];
            this.otherCandidates = new String[others.size()][];
            for (int i = 0; i < others.size(); i++) {
                otherInitials[i] = others.get(i);
                otherCandidates[i] = grouped.get(others.get(i)).toArray(String[]::new);
            }
            this.maxLength = longest;
            this.empty = orderedNames.isEmpty();
        }

        private String[] candidates(char initial) {
            if (initial < 128) {
                return ascii[initial];
            }
            int position = Arrays.binarySearch(otherInitials, initial);
            return position < 0 ? null : otherCandidates[position];
        }
    }

    private record SensitiveFieldValue(int valueStart, int valueEnd) {}

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
                    configuration.set(null);
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
            configuration.set(
                    buildConfiguration(enabledPatternNames, customPatternExpressions, replacement));
        }
    }

    /** 重新从 Logback 或系统属性加载配置，保留既有动态刷新入口。 */
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

    /** 测试钩子：清除快照并恢复全局 Context 引用；生产代码不调用 */
    static void resetForTest(Context context) {
        synchronized (CONFIGURATION_LOCK) {
            configuration.set(null);
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
        MaskConfigurationSnapshot current = configuration.get();
        if (current != null) {
            return current;
        }
        synchronized (CONFIGURATION_LOCK) {
            current = configuration.get();
            if (current == null) {
                current =
                        buildConfiguration(
                                readConfigurationAsList(MASK_ENABLED_PATTERNS_PROPERTY),
                                readConfigurationAsList(MASK_CUSTOM_PATTERNS_PROPERTY),
                                readConfiguration(MASK_REPLACEMENT_PROPERTY));
                configuration.set(current);
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
                List.copyOf(patterns), new FieldNameIndex(keyValueFieldNames), resolvedReplacement);
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
        String result =
                maskKeyValueFields(message, snapshot.keyValueFieldNames(), snapshot.replacement());
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

    private static String maskKeyValueFields(
            String message, FieldNameIndex fieldNames, String replacement) {
        if (fieldNames.empty) {
            return message;
        }
        // 字段只能以“字段名 + 可选引号 + 空白 + '=' 或 ':'”的形式出现，因此只需在每个分隔符之前、最长字段名长度的窗口内
        // 按原逐位置规则判断；窗口外的位置不可能匹配，结果与逐字符扫描一致。
        int maxNameLength = fieldNames.maxLength;
        int length = message.length();
        int nextEquals = message.indexOf('=');
        int nextColon = message.indexOf(':');
        StringBuilder masked = null;
        int copiedUntil = 0;
        int index = 0;
        while (index < length) {
            if (nextEquals >= 0 && nextEquals < index) {
                nextEquals = message.indexOf('=', index);
            }
            if (nextColon >= 0 && nextColon < index) {
                nextColon = message.indexOf(':', index);
            }
            int separator =
                    nextEquals < 0
                            ? nextColon
                            : nextColon < 0 ? nextEquals : Math.min(nextEquals, nextColon);
            if (separator < 0) {
                break;
            }
            int keyEnd = separator;
            while (keyEnd > index && Character.isWhitespace(message.charAt(keyEnd - 1))) {
                keyEnd--;
            }
            if (keyEnd > index && isQuote(message.charAt(keyEnd - 1))) {
                keyEnd--;
            }
            SensitiveFieldValue fieldValue = null;
            for (int start = Math.max(index, keyEnd - maxNameLength); start < keyEnd; start++) {
                fieldValue = findSensitiveFieldValue(message, start, fieldNames);
                if (fieldValue != null) {
                    break;
                }
            }
            if (fieldValue == null) {
                index = separator + 1;
            } else {
                if (masked == null) {
                    masked = new StringBuilder(length);
                }
                masked.append(message, copiedUntil, fieldValue.valueStart());
                appendMaskedFieldValue(masked, message, fieldValue.valueStart(), replacement);
                copiedUntil = fieldValue.valueEnd();
                index = fieldValue.valueEnd();
            }
        }
        return masked == null
                ? message
                : masked.append(message, copiedUntil, message.length()).toString();
    }

    private static SensitiveFieldValue findSensitiveFieldValue(
            String message, int index, FieldNameIndex fieldNames) {
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
        if (cursor >= message.length()
                || (message.charAt(cursor) != '=' && message.charAt(cursor) != ':')) {
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

    private static void appendMaskedFieldValue(
            StringBuilder masked, String message, int valueStart, String replacement) {
        if (isQuote(message.charAt(valueStart))) {
            masked.append(message.charAt(valueStart))
                    .append(replacement)
                    .append(message.charAt(valueStart));
        } else {
            masked.append(replacement);
        }
    }

    private static String matchingFieldName(String message, int index, FieldNameIndex fieldNames) {
        String[] candidates = fieldNames.candidates(message.charAt(index));
        if (candidates == null) {
            return null;
        }
        for (String fieldName : candidates) {
            if (message.regionMatches(true, index, fieldName, 0, fieldName.length())) {
                return fieldName;
            }
        }
        return null;
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
