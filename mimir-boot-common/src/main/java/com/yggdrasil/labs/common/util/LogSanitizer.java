package com.yggdrasil.labs.common.util;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 日志内容清理工具，防止日志注入攻击。
 */
public final class LogSanitizer {

    private LogSanitizer() {
    }

    /**
     * 移除换行与控制字符，防止伪造日志行。
     *
     * @param input 原始输入
     * @return 清理后的字符串，null 返回 "null"
     */
    public static String sanitize(String input) {
        if (input == null) {
            return "null";
        }
        return input.replaceAll("[\\r\\n]", "")
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
    }

    /**
     * 对集合进行批量清理。
     */
    public static List<String> sanitize(Collection<String> inputs) {
        if (inputs == null) {
            return List.of();
        }
        return inputs.stream().map(LogSanitizer::sanitize).collect(Collectors.toList());
    }

    /**
     * 将控制字符转义为可见文本，例如换行符转义为 "\\n"。适合访问日志等场景。
     */
    public static String escapeControls(String input) {
        if (input == null) {
            return null;
        }
        String escaped = input
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
        // 其余不可见控制字符直接移除
        return escaped.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "").trim();
    }

    /**
     * 批量转义控制字符。
     */
    public static List<String> escapeControls(Collection<String> inputs) {
        if (inputs == null) {
            return List.of();
        }
        return inputs.stream().map(LogSanitizer::escapeControls).collect(Collectors.toList());
    }
}


