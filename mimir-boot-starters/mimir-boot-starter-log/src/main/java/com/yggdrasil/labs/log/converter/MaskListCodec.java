package com.yggdrasil.labs.log.converter;

import java.util.ArrayList;
import java.util.List;

/**
 * 脱敏规则列表与 LoggerContext 属性值之间的无损编码（内部实现，非公共 API）
 *
 * <p>不依赖 Logback，可在日志后端检查前加载。编码时顶层逗号转义为 {@code \,}（正则中等价于字面逗号）；拆分只在未转义且不位于 ()、{}、[] 内的逗号处进行，{@code
 * \d{15,18}} 等量词与字符类不被拆开。
 *
 * @author Yggdrasil Labs
 * @since 2.4.0
 */
public final class MaskListCodec {

    private MaskListCodec() {}

    /** 编码规则列表；null 或空列表返回 null */
    public static String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                joined.append(',');
            }
            String value = values.get(i) == null ? "" : values.get(i);
            int start = 0;
            for (int separator : topLevelCommas(value)) {
                joined.append(value, start, separator).append('\\');
                start = separator;
            }
            joined.append(value, start, value.length());
        }
        return joined.toString();
    }

    /** 拆分属性值；调用方负责处理 null 与空字符串 */
    public static List<String> split(String value) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        for (int separator : topLevelCommas(value)) {
            parts.add(value.substring(start, separator));
            start = separator + 1;
        }
        parts.add(value.substring(start));
        return List.copyOf(parts);
    }

    private static List<Integer> topLevelCommas(String value) {
        List<Integer> positions = new ArrayList<>();
        boolean escaped = false;
        boolean inClass = false;
        int depth = 0;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (current == '\\') {
                escaped = true;
            } else if (inClass) {
                inClass = current != ']';
            } else if (current == '[') {
                inClass = true;
            } else if (current == '(' || current == '{') {
                depth++;
            } else if ((current == ')' || current == '}') && depth > 0) {
                depth--;
            } else if (current == ',' && depth == 0) {
                positions.add(i);
            }
        }
        return positions;
    }
}
