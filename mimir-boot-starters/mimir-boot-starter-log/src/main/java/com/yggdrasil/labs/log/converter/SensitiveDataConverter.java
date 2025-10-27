package com.yggdrasil.labs.log.converter;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 敏感信息脱敏转换器
 *
 * <p>功能说明：</p>
 * <ul>
 * <li>在日志输出时自动隐藏敏感信息（密码、账号、身份证号等）</li>
 * <li>支持通过配置自定义敏感信息匹配规则</li>
 * <li>可通过开关控制是否启用脱敏功能</li>
 * </ul>
 *
 * <p>配置方式：</p>
 * <pre>{@code
 * # application.yml
 * mimir:
 *   boot:
 *     log:
 *       mask:
 *         enabled: true                    # 是否启用脱敏
 *         patterns:                        # 脱敏规则（正则表达式）
 *           - password|pwd
 *           - account|accountId
 *           - idcard|idCard|身份证
 *           - phone|mobile|手机号
 *         replacement: "******"           # 替换字符
 * }</pre>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
public class SensitiveDataConverter extends ClassicConverter {

    // 配置属性名称常量
    public static final String MASK_ENABLED_PATTERNS_PROPERTY = "mimir.boot.log.mask.enabledPatterns";
    public static final String MASK_CUSTOM_PATTERNS_PROPERTY = "mimir.boot.log.mask.customPatterns";
    public static final String MASK_REPLACEMENT_PROPERTY = "mimir.boot.log.mask.replacement";

    private static final String DEFAULT_REPLACEMENT = "******";

    private static volatile List<Pattern> patterns;
    private static volatile String replacement;
    private static final List<String> customPatterns = new ArrayList<>();
    private static final Object LOCK = new Object();

    @Override
    public void start() {
        super.start();
    }

    @Override
    public String convert(ILoggingEvent event) {
        String message = event.getFormattedMessage();
        if (message == null || message.isEmpty()) {
            return message;
        }

        return maskSensitiveData(message);
    }

    /**
     * 获取脱敏规则
     * 使用双重检查锁定保证线程安全
     */
    private List<Pattern> getPatterns() {
        List<Pattern> result = patterns;
        if (result == null) {
            synchronized (LOCK) {
                result = patterns;
                if (result == null) {
                    patterns = loadPatterns();
                    result = patterns;
                }
            }
        }
        return result;
    }

    /**
     * 加载脱敏规则
     */
    private List<Pattern> loadPatterns() {
        List<Pattern> result = new ArrayList<>();

        // 1. 加载启用的预置规则
        List<String> enabledNames = getEnabledPatternNames();
        if (!enabledNames.isEmpty()) {
            result.addAll(getPresetPatterns(enabledNames));
        }

        // 2. 加载配置中的自定义规则
        List<String> customPatternsList = getCustomPatterns();
        if (customPatternsList != null && !customPatternsList.isEmpty()) {
            compilePatterns(result, customPatternsList, "Invalid custom mask pattern: ");
        }

        // 3. 加载编程式添加的自定义规则（需要同步复制以确保线程安全）
        List<String> programmaticPatterns;
        synchronized (LOCK) {
            programmaticPatterns = new ArrayList<>(customPatterns);
        }
        if (!programmaticPatterns.isEmpty()) {
            compilePatterns(result, programmaticPatterns, "Invalid programmatic mask pattern: ");
        }

        return result;
    }

    /**
     * 获取启用的预置规则名称
     */
    private List<String> getEnabledPatternNames() {
        return getConfigAsList(MASK_ENABLED_PATTERNS_PROPERTY);
    }

    /**
     * 获取自定义规则列表
     */
    private List<String> getCustomPatterns() {
        return getConfigAsList(MASK_CUSTOM_PATTERNS_PROPERTY);
    }
    
    /**
     * 通用方法：从配置获取列表值
     * 
     * @param key 配置键
     * @return 配置值列表（逗号分隔）
     */
    private List<String> getConfigAsList(String key) {
        List<String> result = new ArrayList<>();

        // 从 Logback context 读取
        String value = getContextProperty(key);
        if (value == null || value.isEmpty()) {
            value = System.getProperty(key);
        }

        if (value != null && !value.isEmpty()) {
            result.addAll(List.of(value.split(",")));
        }

        return result;
    }
    
    /**
     * 通用方法：编译正则表达式模式
     * 
     * @param targetPatterns 目标列表
     * @param patternStrings 正则表达式字符串列表
     * @param errorPrefix 错误信息前缀
     */
    private void compilePatterns(List<Pattern> targetPatterns, List<String> patternStrings, String errorPrefix) {
        if (patternStrings == null || patternStrings.isEmpty()) {
            return;
        }
        
        for (String patternStr : patternStrings) {
            try {
                targetPatterns.add(Pattern.compile(patternStr.trim()));
            } catch (Exception e) {
                System.err.println(errorPrefix + patternStr);
            }
        }
    }

    /**
     * 获取启用的预置规则
     */
    private List<Pattern> getPresetPatterns(List<String> enabledNames) {
        List<Pattern> patterns = new ArrayList<>();

        for (String name : enabledNames) {
            SensitiveDataPattern patternEnum = SensitiveDataPattern.fromName(name.trim());
            if (patternEnum != null) {
                try {
                    patterns.add(Pattern.compile(patternEnum.getPattern()));
                } catch (Exception e) {
                    System.err.println("Invalid preset pattern: " + name);
                }
            }
        }

        return patterns;
    }

    /**
     * 添加自定义规则（编程式扩展）
     *
     * @param pattern 正则表达式
     */
    public static void addCustomPattern(String pattern) {
        synchronized (LOCK) {
            customPatterns.add(pattern);
            patterns = null; // 清空缓存，重新加载
        }
    }

    /**
     * 清空自定义规则
     */
    public static void clearCustomPatterns() {
        synchronized (LOCK) {
            customPatterns.clear();
            patterns = null; // 清空缓存，重新加载
        }
    }

    /**
     * 获取替换字符
     */
    private String getReplacement() {
        if (replacement == null) {
            String value = getContextProperty(MASK_REPLACEMENT_PROPERTY);
            if (value == null || value.isEmpty()) {
                value = System.getProperty(MASK_REPLACEMENT_PROPERTY, DEFAULT_REPLACEMENT);
            }
            replacement = value;
        }
        return replacement;
    }

    /**
     * 从 Logback context 获取属性
     */
    private String getContextProperty(String key) {
        if (getContext() != null) {
            return getContext().getProperty(key);
        }
        return null;
    }

    /**
     * 对敏感信息进行脱敏
     * 
     * @param message 原始消息
     * @return 脱敏后的消息
     */
    public String maskSensitiveData(String message) {
        if (message == null) {
            return null;
        }
        if (message.isEmpty()) {
            return message;
        }
        
        String result = message;

        for (Pattern pattern : getPatterns()) {
            result = pattern.matcher(result).replaceAll(match -> {
                String matched = match.group();
                return maskValue(matched);
            });
        }

        return result;
    }

    /**
     * 对匹配到的值进行脱敏处理
     */
    private String maskValue(String matched) {
        String replacement = getReplacement();

        // 提取等号/冒号后的值进行替换
        if (matched.contains("=")) {
            int index = matched.indexOf("=");
            String prefix = matched.substring(0, index + 1);  // 包含等号的前缀

            // 保留原始值中的引号格式
            String suffix = matched.substring(index + 1);
            boolean hasQuote = suffix.startsWith("\"") || suffix.startsWith("'");
            String quote = hasQuote ? suffix.substring(0, 1) : "";

            // 返回: 前缀 + 引号(如果有) + 替换字符 + 引号(如果有)
            return prefix + quote + replacement + quote;
        } else {
            // 纯数字或其他格式，直接替换整个匹配项
            return replacement;
        }
    }

    /**
     * 重新加载配置（用于配置动态更新）
     */
    public static void reloadConfig() {
        patterns = null;
        replacement = null;
    }

    /**
     * 获取所有可用的预置规则
     *
     * @return 所有预置规则的名称列表
     */
    public static List<String> getAllPresetPatternNames() {
        List<String> names = new ArrayList<>();
        for (SensitiveDataPattern pattern : SensitiveDataPattern.values()) {
            names.add(pattern.getName());
        }
        return names;
    }
}

